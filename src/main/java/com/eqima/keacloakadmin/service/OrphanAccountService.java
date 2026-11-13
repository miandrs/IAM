package com.eqima.keacloakadmin.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.exception.ConflictException;
import com.eqima.keacloakadmin.exception.InvalidRequestException;
import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.models.dto.OrphanFindingDto;
import com.eqima.keacloakadmin.models.dto.OrphanRemediationResultDto;
import com.eqima.keacloakadmin.models.dto.OrphanReportDto;
import com.eqima.keacloakadmin.models.entity.AccessRequest;
import com.eqima.keacloakadmin.models.entity.AccessRequestGate;
import com.eqima.keacloakadmin.models.entity.AccessRequestStatus;
import com.eqima.keacloakadmin.models.entity.BusinessProfile;
import com.eqima.keacloakadmin.models.entity.Organization;
import com.eqima.keacloakadmin.models.entity.OrphanType;
import com.eqima.keacloakadmin.models.entity.UserProfileAssignment;
import com.eqima.keacloakadmin.models.request.OrphanRemediationRequest;
import com.eqima.keacloakadmin.repository.AccessRequestGateRepository;
import com.eqima.keacloakadmin.repository.AccessRequestRepository;
import com.eqima.keacloakadmin.repository.BusinessProfileRepository;
import com.eqima.keacloakadmin.repository.OrganizationRepository;
import com.eqima.keacloakadmin.repository.UserProfileAssignmentRepository;
import com.eqima.keacloakadmin.util.Details;
import com.eqima.keacloakadmin.util.SecurityUtils;

import jakarta.ws.rs.NotFoundException;

/**
 * Détection (et remédiation) des comptes / références orphelins. Jamais de suppression : au pire un compte est
 * désactivé et ses sessions coupées, une attribution est clôturée. Les orphelins "côté base" (utilisateur
 * Keycloak supprimé alors que UserService.deleteUser ne nettoie rien) ne sont visibles que du SUPERADMIN et du
 * scheduler : sans compte, on ne sait plus à quelle organisation les rattacher.
 */
@Service
public class OrphanAccountService {

    private static final int PAGE = 100;
    private static final String SERVICE_ACCOUNT_PREFIX = "service-account-";

    private final Keycloak keycloak;
    private final UserScopeService userScope;
    private final UserService userService;
    private final ProfileRevocationService revocationService;
    private final OrganizationRepository organizationRepository;
    private final UserProfileAssignmentRepository assignmentRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final AccessRequestGateRepository gateRepository;
    private final BusinessProfileRepository profileRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    @Value("${keycloak.realm}")
    private String realmName;

    public OrphanAccountService(@Qualifier("keycloakClient") Keycloak keycloak,
                                UserScopeService userScope,
                                UserService userService,
                                ProfileRevocationService revocationService,
                                OrganizationRepository organizationRepository,
                                UserProfileAssignmentRepository assignmentRepository,
                                AccessRequestRepository accessRequestRepository,
                                AccessRequestGateRepository gateRepository,
                                BusinessProfileRepository profileRepository,
                                AuditService auditService,
                                SecurityUtils securityUtils) {
        this.keycloak = keycloak;
        this.userScope = userScope;
        this.userService = userService;
        this.revocationService = revocationService;
        this.organizationRepository = organizationRepository;
        this.assignmentRepository = assignmentRepository;
        this.accessRequestRepository = accessRequestRepository;
        this.gateRepository = gateRepository;
        this.profileRepository = profileRepository;
        this.auditService = auditService;
        this.securityUtils = securityUtils;
    }

    // État partagé d'un passage de scan (évite de relire les groupes / organisations pour chaque utilisateur)
    private record Ctx(Map<String, String> topByName,
                       Map<String, Organization> orgByGroupId,
                       Set<String> superAdminIds,
                       Map<String, List<UserProfileAssignment>> activeByUser) {}

    // ═══════════════ Scan ═══════════════

    public OrphanReportDto scanForCurrentUser() {
        if (securityUtils.isSuperAdmin()) return doScan(null, true);
        if (securityUtils.isAdmin()) {
            Set<UUID> mine = userScope.callerOrganizationIds();
            if (mine.isEmpty()) return buildReport(0, List.of());
            return doScan(mine, false);
        }
        throw new AccessDeniedException("Not allowed to scan orphan accounts");
    }

    /** Pour le scheduler (pas de JWT) : périmètre complet. */
    public OrphanReportDto scanAsSystem() {
        return doScan(null, true);
    }

    private OrphanReportDto doScan(Set<UUID> allowedOrgs, boolean includeDbOrphans) {
        Ctx ctx = buildCtx();
        RealmResource realm = keycloak.realm(realmName);

        List<OrphanFindingDto> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int first = 0;
        while (true) {
            List<UserRepresentation> page = realm.users().list(first, PAGE);
            if (page == null || page.isEmpty()) break;
            for (UserRepresentation u : page) {
                seen.add(u.getId());
                findings.addAll(evaluateUser(ctx, u, allowedOrgs));
            }
            if (page.size() < PAGE) break;
            first += PAGE;
        }

        if (includeDbOrphans) findings.addAll(findDbOrphans(ctx, seen));

        Integer total = realm.users().count();
        return buildReport(total != null ? total : seen.size(), findings);
    }

    private OrphanReportDto buildReport(int totalUsers, List<OrphanFindingDto> findings) {
        Map<String, Long> counts = findings.stream()
            .collect(Collectors.groupingBy(OrphanFindingDto::type, TreeMap::new, Collectors.counting()));
        return new OrphanReportDto(Instant.now(), totalUsers, findings.size(), counts, findings);
    }

    private Ctx buildCtx() {
        Map<String, Organization> orgByGroupId = organizationRepository.findAll().stream()
            .collect(Collectors.toMap(Organization::getKeycloakGroupId, o -> o, (a, b) -> a));
        Map<String, List<UserProfileAssignment>> activeByUser = assignmentRepository.findByActiveTrue().stream()
            .collect(Collectors.groupingBy(UserProfileAssignment::getUserUuid));
        return new Ctx(userScope.topLevelGroupIdsByName(), orgByGroupId, loadSuperAdminIds(), activeByUser);
    }

    private Set<String> loadSuperAdminIds() {
        Set<String> ids = new HashSet<>();
        try {
            int first = 0;
            while (true) {
                List<UserRepresentation> page = keycloak.realm(realmName).roles().get("SUPERADMIN").getUserMembers(first, PAGE);
                if (page == null || page.isEmpty()) break;
                page.forEach(u -> ids.add(u.getId()));
                if (page.size() < PAGE) break;
                first += PAGE;
            }
        } catch (NotFoundException e) {
            // rôle SUPERADMIN absent du realm : aucune exemption
        }
        return ids;
    }

    private List<OrphanFindingDto> evaluateUser(Ctx ctx, UserRepresentation u, Set<UUID> allowedOrgs) {
        String username = u.getUsername();
        if (ctx.superAdminIds().contains(u.getId())
            || (username != null && username.startsWith(SERVICE_ACCOUNT_PREFIX))) {
            return List.of(); // comptes de plateforme / de service : hors périmètre des orphelins
        }

        List<GroupRepresentation> groups = keycloak.realm(realmName).users().get(u.getId()).groups();
        List<Organization> orgs = new ArrayList<>();
        for (GroupRepresentation g : groups) {
            String top = g.getPath() != null ? UserScopeService.topSegment(g.getPath()) : g.getName();
            String groupId = ctx.topByName().get(top.toUpperCase(Locale.ROOT));
            Organization org = groupId != null ? ctx.orgByGroupId().get(groupId) : null;
            if (org != null) orgs.add(org);
        }
        if (allowedOrgs != null && orgs.stream().noneMatch(o -> allowedOrgs.contains(o.getId()))) {
            return List.of();
        }

        boolean enabled = Boolean.TRUE.equals(u.isEnabled());
        List<UserProfileAssignment> active = ctx.activeByUser().getOrDefault(u.getId(), List.of());
        List<OrphanFindingDto> out = new ArrayList<>();

        if (enabled && groups.isEmpty()) {
            out.add(finding(OrphanType.NO_ORGANIZATION, active.isEmpty() ? "MEDIUM" : "HIGH", u.getId(), username, null,
                "Compte actif n'appartenant à aucun groupe/organisation."
                    + (active.isEmpty() ? "" : " Il détient " + active.size() + " profil(s) actif(s)."),
                "DISABLE_ACCOUNT"));
        } else if (enabled && orgs.stream().noneMatch(Organization::isActive)) {
            out.add(finding(OrphanType.ORGANIZATION_INACTIVE_OR_UNTRACKED, "HIGH", u.getId(), username, null,
                "Compte actif dont aucune organisation n'est active ou suivie (groupes : "
                    + groups.stream().map(GroupRepresentation::getName).collect(Collectors.joining(", ")) + ").",
                "DISABLE_ACCOUNT"));
        }

        if (!enabled && !active.isEmpty()) {
            out.add(finding(OrphanType.DISABLED_USER_ACTIVE_PROFILE, "HIGH", u.getId(), username, null,
                "Compte désactivé qui conserve " + active.size() + " profil(s) actif(s).", "REVOKE_ASSIGNMENTS"));
        }
        return out;
    }

    private List<OrphanFindingDto> findDbOrphans(Ctx ctx, Set<String> seen) {
        List<OrphanFindingDto> out = new ArrayList<>();
        Map<String, Boolean> exists = new HashMap<>();
        java.util.function.Predicate<String> userExists =
            id -> seen.contains(id) || exists.computeIfAbsent(id, userScope::userExists);

        List<UserProfileAssignment> allActive = ctx.activeByUser().values().stream().flatMap(List::stream).toList();

        for (UserProfileAssignment a : allActive) {
            if (!userExists.test(a.getUserUuid())) {
                out.add(finding(OrphanType.ASSIGNMENT_USER_MISSING, "HIGH", a.getUserUuid(), null, a.getId(),
                    "Attribution active du profil " + a.getProfileId() + " pour un utilisateur supprimé de Keycloak.",
                    "CLOSE_ASSIGNMENT"));
            }
        }

        for (AccessRequest r : accessRequestRepository.findByStatus(AccessRequestStatus.PENDING)) {
            if (!userExists.test(r.getUserUuid())) {
                out.add(finding(OrphanType.ACCESS_REQUEST_USER_MISSING, "MEDIUM", r.getUserUuid(), null, r.getId(),
                    "Demande d'accès en attente pour un utilisateur supprimé de Keycloak.", "REJECT_REQUEST"));
            }
        }

        Set<UUID> profileIds = allActive.stream().map(UserProfileAssignment::getProfileId).collect(Collectors.toSet());
        Set<UUID> knownProfiles = profileRepository.findAllById(profileIds).stream()
            .map(BusinessProfile::getId).collect(Collectors.toSet());
        for (UserProfileAssignment a : allActive) {
            // si le compte est déjà signalé comme supprimé, inutile de doubler le constat
            if (!knownProfiles.contains(a.getProfileId()) && userExists.test(a.getUserUuid())) {
                out.add(finding(OrphanType.ASSIGNMENT_PROFILE_MISSING, "MEDIUM", a.getUserUuid(), null, a.getId(),
                    "Attribution active dont le profil " + a.getProfileId() + " a été supprimé.", "CLOSE_ASSIGNMENT"));
            }
        }
        return out;
    }

    private OrphanFindingDto finding(OrphanType type, String severity, String userUuid, String username,
                                     UUID referenceId, String detail, String suggestedAction) {
        return new OrphanFindingDto(type.name(), severity, userUuid, username, referenceId, detail, suggestedAction);
    }

    // ═══════════════ Remédiation (SUPERADMIN) ═══════════════

    public OrphanRemediationResultDto remediate(OrphanRemediationRequest req) {
        if (!securityUtils.isSuperAdmin()) {
            throw new AccessDeniedException("Réservé au SUPERADMIN");
        }
        OrphanType type = req.type();
        String targetUser = req.userUuid();

        String action = switch (type) {
            case NO_ORGANIZATION, ORGANIZATION_INACTIVE_OR_UNTRACKED -> {
                UserRepresentation u = requireStillOrphan(req, type);
                userService.disableOrEnableUser(u.getId(), false);
                userService.sessionRevoke(u.getId());
                yield "ACCOUNT_DISABLED";
            }
            case DISABLED_USER_ACTIVE_PROFILE -> {
                UserRepresentation u = requireStillOrphan(req, type);
                for (UserProfileAssignment a : assignmentRepository.findByUserUuidAndActiveTrue(u.getId())) {
                    revocationService.revoke(a, "Compte désactivé : nettoyage des attributions");
                }
                yield "ASSIGNMENTS_REVOKED";
            }
            case ASSIGNMENT_USER_MISSING -> {
                UserProfileAssignment a = loadActiveAssignment(req);
                targetUser = a.getUserUuid();
                if (userScope.userExists(a.getUserUuid())) {
                    throw new ConflictException("L'utilisateur existe à nouveau dans Keycloak : constat obsolète.");
                }
                revocationService.revokeLocalOnly(a, "Orphelin : utilisateur supprimé de Keycloak");
                yield "ASSIGNMENT_CLOSED";
            }
            case ASSIGNMENT_PROFILE_MISSING -> {
                UserProfileAssignment a = loadActiveAssignment(req);
                targetUser = a.getUserUuid();
                if (profileRepository.existsById(a.getProfileId())) {
                    throw new ConflictException("Le profil existe : constat obsolète.");
                }
                revocationService.revokeLocalOnly(a, "Orphelin : profil supprimé");
                yield "ASSIGNMENT_CLOSED";
            }
            case ACCESS_REQUEST_USER_MISSING -> {
                if (req.referenceId() == null) throw new InvalidRequestException("referenceId (id de la demande) requis.");
                AccessRequest r = accessRequestRepository.findById(req.referenceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Demande d'accès introuvable : " + req.referenceId()));
                targetUser = r.getUserUuid();
                if (r.getStatus() != AccessRequestStatus.PENDING) {
                    throw new ConflictException("La demande n'est plus en attente : constat obsolète.");
                }
                if (userScope.userExists(r.getUserUuid())) {
                    throw new ConflictException("L'utilisateur existe à nouveau dans Keycloak : constat obsolète.");
                }
                String me = securityUtils.currentUserId();
                for (AccessRequestGate g : gateRepository.findByAccessRequestId(r.getId())) {
                    if ("PENDING".equals(g.getDecision())) {
                        g.setDecision("REJECTED");
                        g.setApproverUuid(me);
                        g.setComment("Utilisateur supprimé de Keycloak");
                        g.setDecidedAt(Instant.now());
                        gateRepository.save(g);
                    }
                }
                r.setStatus(AccessRequestStatus.REJECTED);
                r.setDecidedAt(Instant.now());
                accessRequestRepository.save(r);
                yield "REQUEST_REJECTED";
            }
        };

        auditService.log("ORPHAN_REMEDIATED", targetUser,
            Details.of("type", type, "referenceId", req.referenceId(), "action", action));
        return new OrphanRemediationResultDto(type.name(), targetUser, req.referenceId(), action);
    }

    // Re-vérifie le constat au moment d'agir : le rapport a pu vieillir entre la lecture et le clic.
    private UserRepresentation requireStillOrphan(OrphanRemediationRequest req, OrphanType type) {
        if (req.userUuid() == null || req.userUuid().isBlank()) {
            throw new InvalidRequestException("userUuid requis pour le type " + type);
        }
        UserRepresentation u = userScope.findUser(req.userUuid())
            .orElseThrow(() -> new ResourceNotFoundException("Utilisateur Keycloak introuvable : " + req.userUuid()));
        boolean still = evaluateUser(buildCtx(), u, null).stream().anyMatch(f -> f.type().equals(type.name()));
        if (!still) {
            throw new ConflictException("Ce compte n'est plus orphelin pour le motif " + type + " : constat obsolète.");
        }
        return u;
    }

    private UserProfileAssignment loadActiveAssignment(OrphanRemediationRequest req) {
        if (req.referenceId() == null) throw new InvalidRequestException("referenceId (id de l'attribution) requis.");
        UserProfileAssignment a = assignmentRepository.findById(req.referenceId())
            .orElseThrow(() -> new ResourceNotFoundException("Attribution introuvable : " + req.referenceId()));
        if (!a.isActive()) throw new ConflictException("L'attribution est déjà clôturée.");
        return a;
    }
}
