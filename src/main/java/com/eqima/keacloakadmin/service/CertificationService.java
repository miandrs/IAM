package com.eqima.keacloakadmin.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eqima.keacloakadmin.exception.ConflictException;
import com.eqima.keacloakadmin.exception.InvalidRequestException;
import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.models.dto.CertificationCampaignDto;
import com.eqima.keacloakadmin.models.dto.CertificationItemDto;
import com.eqima.keacloakadmin.models.entity.AccessCertificationCampaign;
import com.eqima.keacloakadmin.models.entity.AccessCertificationItem;
import com.eqima.keacloakadmin.models.entity.BusinessProfile;
import com.eqima.keacloakadmin.models.entity.CertificationDecision;
import com.eqima.keacloakadmin.models.entity.CertificationStatus;
import com.eqima.keacloakadmin.models.entity.UserProfileAssignment;
import com.eqima.keacloakadmin.models.request.StartCertificationRequest;
import com.eqima.keacloakadmin.repository.AccessCertificationCampaignRepository;
import com.eqima.keacloakadmin.repository.AccessCertificationItemRepository;
import com.eqima.keacloakadmin.repository.BusinessProfileRepository;
import com.eqima.keacloakadmin.repository.OrganizationRepository;
import com.eqima.keacloakadmin.repository.UserProfileAssignmentRepository;
import com.eqima.keacloakadmin.util.Details;
import com.eqima.keacloakadmin.util.SecurityUtils;

import jakarta.ws.rs.NotFoundException;

/**
 * Certification périodique des accès : une campagne fige les attributions de profil actives (snapshot), puis
 * un relecteur certifie ou révoque chacune. Les campagnes planifiées (GovernanceScheduler) n'ont pas de JWT :
 * elles n'écrivent que via AuditService.logSystem et ne révoquent jamais automatiquement.
 */
@Service
public class CertificationService {

    private static final Logger log = LoggerFactory.getLogger(CertificationService.class);

    private final AccessCertificationCampaignRepository campaignRepository;
    private final AccessCertificationItemRepository itemRepository;
    private final UserProfileAssignmentRepository assignmentRepository;
    private final BusinessProfileRepository profileRepository;
    private final OrganizationRepository organizationRepository;
    private final UserScopeService userScope;
    private final ProfileRevocationService revocationService;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;
    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realmName;

    @Value("${governance.certification.default-due-days:30}")
    private int defaultDueDays;

    public CertificationService(AccessCertificationCampaignRepository campaignRepository,
                                AccessCertificationItemRepository itemRepository,
                                UserProfileAssignmentRepository assignmentRepository,
                                BusinessProfileRepository profileRepository,
                                OrganizationRepository organizationRepository,
                                UserScopeService userScope,
                                ProfileRevocationService revocationService,
                                AuditService auditService,
                                SecurityUtils securityUtils,
                                @Qualifier("keycloakClient") Keycloak keycloak) {
        this.campaignRepository = campaignRepository;
        this.itemRepository = itemRepository;
        this.assignmentRepository = assignmentRepository;
        this.profileRepository = profileRepository;
        this.organizationRepository = organizationRepository;
        this.userScope = userScope;
        this.revocationService = revocationService;
        this.auditService = auditService;
        this.securityUtils = securityUtils;
        this.keycloak = keycloak;
    }

    // ═══════════════ Lancement ═══════════════

    @Transactional
    public CertificationCampaignDto start(StartCertificationRequest req) {
        boolean superAdmin = securityUtils.isSuperAdmin();
        if (!superAdmin && !securityUtils.isAdmin()) {
            throw new AccessDeniedException("Not allowed to start a certification campaign");
        }

        UUID orgFilter = req != null ? req.organizationId() : null;
        if (orgFilter != null) {
            organizationRepository.findById(orgFilter)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation inexistante : " + orgFilter));
        }

        Set<UUID> allowedOrgs; // null = aucun filtre
        if (superAdmin) {
            allowedOrgs = orgFilter != null ? Set.of(orgFilter) : null;
        } else {
            Set<UUID> mine = userScope.callerOrganizationIds();
            if (mine.isEmpty()) throw new AccessDeniedException("Aucune organisation dans votre périmètre.");
            if (orgFilter != null && !mine.contains(orgFilter)) {
                throw new AccessDeniedException("Organisation hors de votre périmètre.");
            }
            allowedOrgs = orgFilter != null ? Set.of(orgFilter) : mine;
        }

        int dueDays = req != null && req.dueInDays() != null ? req.dueInDays() : defaultDueDays;
        String name = req != null && req.name() != null && !req.name().isBlank()
            ? req.name().trim() : "Certification des accès " + LocalDate.now();

        AccessCertificationCampaign campaign = launch(name, Instant.now().plus(dueDays, ChronoUnit.DAYS),
            orgFilter, allowedOrgs, securityUtils.currentUserId(), false);
        if (campaign == null) {
            throw new ConflictException("Aucune attribution active à certifier dans ce périmètre "
                + "(ou toutes sont déjà dans une campagne ouverte).");
        }
        return toDto(campaign);
    }

    /** Appelée par le scheduler (pas de JWT). Ne crée rien s'il existe déjà une campagne ouverte. */
    @Transactional
    public Optional<AccessCertificationCampaign> launchPeriodicCampaign() {
        if (!campaignRepository.findByStatus(CertificationStatus.OPEN).isEmpty()) {
            log.info("Certification périodique ignorée : une campagne est déjà ouverte.");
            return Optional.empty();
        }
        AccessCertificationCampaign c = launch("Certification périodique " + LocalDate.now(),
            Instant.now().plus(defaultDueDays, ChronoUnit.DAYS), null, null, "SYSTEM", true);
        return Optional.ofNullable(c);
    }

    private AccessCertificationCampaign launch(String name, Instant dueAt, UUID scopeOrgId,
                                               Set<UUID> allowedOrgs, String createdBy, boolean system) {
        Set<UUID> alreadyPending = itemRepository.findByDecision(CertificationDecision.PENDING).stream()
            .map(AccessCertificationItem::getAssignmentId).collect(Collectors.toSet());

        List<UserProfileAssignment> candidates = assignmentRepository.findByActiveTrue().stream()
            .filter(a -> !alreadyPending.contains(a.getId())).toList();

        Map<UUID, String> profileNames = profileRepository.findAllById(
                candidates.stream().map(UserProfileAssignment::getProfileId).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(BusinessProfile::getId, BusinessProfile::getName));

        Map<String, String> topByName = allowedOrgs != null ? userScope.topLevelGroupIdsByName() : Map.of();
        Map<String, Optional<String>> usernames = new HashMap<>();
        Map<String, Set<UUID>> orgsByUser = new HashMap<>();
        List<AccessCertificationItem> items = new ArrayList<>();

        for (UserProfileAssignment a : candidates) {
            Optional<String> username = usernames.computeIfAbsent(a.getUserUuid(), this::lookupUsername);
            if (allowedOrgs != null) {
                // un compte supprimé n'a plus d'organisation : seul un SUPERADMIN / le système le voit (cf. orphelins)
                if (username.isEmpty()) continue;
                Set<UUID> orgs = orgsByUser.computeIfAbsent(a.getUserUuid(),
                    u -> userScope.organizationIdsOfUser(u, topByName));
                if (Collections.disjoint(orgs, allowedOrgs)) continue;
            }
            AccessCertificationItem item = new AccessCertificationItem();
            item.setAssignmentId(a.getId());
            item.setUserUuid(a.getUserUuid());
            item.setUsernameSnapshot(username.orElse("(utilisateur introuvable)"));
            item.setProfileId(a.getProfileId());
            item.setProfileNameSnapshot(profileNames.getOrDefault(a.getProfileId(), "(profil introuvable)"));
            items.add(item);
        }

        if (items.isEmpty()) return null;

        AccessCertificationCampaign campaign = new AccessCertificationCampaign();
        campaign.setName(name);
        campaign.setStatus(CertificationStatus.OPEN);
        campaign.setScopeOrganizationId(scopeOrgId);
        campaign.setCreatedBy(createdBy);
        campaign.setDueAt(dueAt);
        AccessCertificationCampaign saved = campaignRepository.save(campaign);

        items.forEach(i -> i.setCampaignId(saved.getId()));
        itemRepository.saveAll(items);

        var details = Details.of("campaignId", saved.getId(), "name", name, "items", items.size(),
            "scopeOrganizationId", scopeOrgId, "dueAt", dueAt);
        if (system) auditService.logSystem("START_CERTIFICATION", null, null, details);
        else auditService.log("START_CERTIFICATION", null, null, details);
        return saved;
    }

    private Optional<String> lookupUsername(String userUuid) {
        try {
            return Optional.ofNullable(keycloak.realm(realmName).users().get(userUuid).toRepresentation().getUsername());
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }

    // ═══════════════ Consultation ═══════════════

    public List<CertificationCampaignDto> listCampaigns() {
        requireAdminOrSuperAdmin();
        return campaignRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    public CertificationCampaignDto getCampaign(UUID id) {
        requireAdminOrSuperAdmin();
        return toDto(findCampaign(id));
    }

    public List<CertificationItemDto> listItems(UUID campaignId, CertificationDecision decision) {
        requireAdminOrSuperAdmin();
        findCampaign(campaignId);
        List<AccessCertificationItem> items = decision != null
            ? itemRepository.findByCampaignIdAndDecision(campaignId, decision)
            : itemRepository.findByCampaignId(campaignId);
        return filterByScope(items).stream().map(this::toItemDto).toList();
    }

    /** Éléments à relire, toutes campagnes ouvertes confondues, limités au périmètre du relecteur. */
    public List<CertificationItemDto> listPendingForCurrentReviewer() {
        requireAdminOrSuperAdmin();
        return filterByScope(itemRepository.findByDecision(CertificationDecision.PENDING))
            .stream().map(this::toItemDto).toList();
    }

    private List<AccessCertificationItem> filterByScope(List<AccessCertificationItem> items) {
        if (securityUtils.isSuperAdmin()) return items;
        Set<UUID> mine = userScope.callerOrganizationIds();
        if (mine.isEmpty()) return List.of();
        Map<String, String> top = userScope.topLevelGroupIdsByName();
        Map<String, Boolean> visible = new HashMap<>();
        return items.stream()
            .filter(i -> visible.computeIfAbsent(i.getUserUuid(),
                u -> !Collections.disjoint(mine, userScope.organizationIdsOfUser(u, top))))
            .toList();
    }

    // ═══════════════ Décisions ═══════════════

    public CertificationItemDto certify(UUID itemId, String comment) {
        return decide(itemId, CertificationDecision.CERTIFIED, comment);
    }

    public CertificationItemDto revoke(UUID itemId, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new InvalidRequestException("Un commentaire est obligatoire pour révoquer un accès.");
        }
        return decide(itemId, CertificationDecision.REVOKED, comment);
    }

    private CertificationItemDto decide(UUID itemId, CertificationDecision decision, String comment) {
        requireAdminOrSuperAdmin();
        AccessCertificationItem item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Élément de certification introuvable : " + itemId));
        AccessCertificationCampaign campaign = findCampaign(item.getCampaignId());

        if (campaign.getStatus() != CertificationStatus.OPEN) {
            throw new ConflictException("La campagne est clôturée.");
        }
        if (item.getDecision() != CertificationDecision.PENDING) {
            throw new ConflictException("Cet élément a déjà été traité : " + item.getDecision());
        }

        String me = securityUtils.currentUserId();
        if (me.equals(item.getUserUuid())) {
            throw new AccessDeniedException("Vous ne pouvez pas certifier ou révoquer vos propres accès.");
        }
        if (!userScope.callerCanManage(item.getUserUuid())) {
            throw new AccessDeniedException("Cet utilisateur ne relève pas de vos organisations.");
        }

        UserProfileAssignment assignment = assignmentRepository.findById(item.getAssignmentId()).orElse(null);

        if (decision == CertificationDecision.CERTIFIED) {
            if (assignment == null || !assignment.isActive()) {
                throw new ConflictException("L'attribution n'est plus active : seule la révocation peut être enregistrée.");
            }
            if (!userScope.userExists(item.getUserUuid())) {
                throw new ConflictException("Le compte n'existe plus dans Keycloak : révoquez l'attribution.");
            }
            assignment.setLastCertifiedAt(Instant.now());
            assignmentRepository.save(assignment);
        } else if (assignment != null) {
            if (userScope.userExists(item.getUserUuid())) {
                revocationService.revoke(assignment, "Certification : " + comment);
            } else {
                revocationService.revokeLocalOnly(assignment, "Certification (compte supprimé) : " + comment);
            }
        }

        item.setDecision(decision);
        item.setDecidedBy(me);
        item.setDecidedAt(Instant.now());
        item.setComment(comment);
        itemRepository.save(item);

        auditService.log(decision == CertificationDecision.CERTIFIED ? "CERTIFY_ACCESS" : "REVOKE_ACCESS_CERTIFICATION",
            item.getUserUuid(), item.getUsernameSnapshot(),
            Details.of("campaignId", item.getCampaignId(), "itemId", item.getId(),
                "assignmentId", item.getAssignmentId(), "profileId", item.getProfileId(), "comment", comment));

        return toItemDto(item);
    }

    // ═══════════════ Clôture ═══════════════

    /**
     * Clôture manuelle. Les éléments non traités passent en EXPIRED, ou en REVOKED (avec retrait réel des
     * rôles) si revokeUnreviewed = true. Reprenable : en cas d'échec en cours de route, la campagne reste
     * ouverte et un nouvel appel ne traite que les éléments encore PENDING.
     */
    public CertificationCampaignDto close(UUID campaignId, boolean revokeUnreviewed) {
        requireAdminOrSuperAdmin();
        AccessCertificationCampaign campaign = findCampaign(campaignId);
        if (campaign.getStatus() != CertificationStatus.OPEN) {
            throw new ConflictException("La campagne est déjà clôturée.");
        }
        String me = securityUtils.currentUserId();
        if (!securityUtils.isSuperAdmin() && !me.equals(campaign.getCreatedBy())) {
            throw new AccessDeniedException("Seul le lanceur de la campagne ou un SUPERADMIN peut la clôturer.");
        }

        for (AccessCertificationItem item : itemRepository.findByCampaignIdAndDecision(campaignId, CertificationDecision.PENDING)) {
            if (revokeUnreviewed) {
                UserProfileAssignment a = assignmentRepository.findById(item.getAssignmentId()).orElse(null);
                if (a != null) {
                    if (userScope.userExists(a.getUserUuid())) revocationService.revoke(a, "Non revu à la clôture de la campagne");
                    else revocationService.revokeLocalOnly(a, "Non revu à la clôture (compte supprimé)");
                }
                item.setDecision(CertificationDecision.REVOKED);
                item.setComment("Non revu à la clôture");
            } else {
                item.setDecision(CertificationDecision.EXPIRED);
            }
            item.setDecidedBy(me);
            item.setDecidedAt(Instant.now());
            itemRepository.save(item);
        }

        campaign.setStatus(CertificationStatus.CLOSED);
        campaign.setClosedAt(Instant.now());
        campaign.setClosedBy(me);
        campaignRepository.save(campaign);

        auditService.log("CLOSE_CERTIFICATION", null, null,
            Details.of("campaignId", campaignId, "revokeUnreviewed", revokeUnreviewed));
        return toDto(campaign);
    }

    /** Scheduler : clôture les campagnes échues, éléments restants -> EXPIRED. Aucune révocation automatique. */
    @Transactional
    public int closeOverdueCampaigns() {
        List<AccessCertificationCampaign> overdue =
            campaignRepository.findByStatusAndDueAtBefore(CertificationStatus.OPEN, Instant.now());
        for (AccessCertificationCampaign c : overdue) {
            long expired = 0;
            for (AccessCertificationItem item : itemRepository.findByCampaignIdAndDecision(c.getId(), CertificationDecision.PENDING)) {
                item.setDecision(CertificationDecision.EXPIRED);
                item.setDecidedBy("SYSTEM");
                item.setDecidedAt(Instant.now());
                itemRepository.save(item);
                expired++;
            }
            c.setStatus(CertificationStatus.CLOSED);
            c.setClosedAt(Instant.now());
            c.setClosedBy("SYSTEM");
            campaignRepository.save(c);
            auditService.logSystem("CLOSE_CERTIFICATION", null, null,
                Details.of("campaignId", c.getId(), "reason", "OVERDUE", "expiredItems", expired));
        }
        return overdue.size();
    }

    // ═══════════════ Helpers ═══════════════

    private void requireAdminOrSuperAdmin() {
        if (!securityUtils.isSuperAdmin() && !securityUtils.isAdmin()) {
            throw new AccessDeniedException("Not allowed to access certifications");
        }
    }

    private AccessCertificationCampaign findCampaign(UUID id) {
        return campaignRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Campagne de certification introuvable : " + id));
    }

    private CertificationCampaignDto toDto(AccessCertificationCampaign c) {
        long pending = itemRepository.countByCampaignIdAndDecision(c.getId(), CertificationDecision.PENDING);
        long certified = itemRepository.countByCampaignIdAndDecision(c.getId(), CertificationDecision.CERTIFIED);
        long revoked = itemRepository.countByCampaignIdAndDecision(c.getId(), CertificationDecision.REVOKED);
        long expired = itemRepository.countByCampaignIdAndDecision(c.getId(), CertificationDecision.EXPIRED);
        return new CertificationCampaignDto(c.getId(), c.getName(), c.getStatus().name(), c.getScopeOrganizationId(),
            c.getCreatedBy(), c.getCreatedAt(), c.getDueAt(), c.getClosedAt(),
            pending + certified + revoked + expired, pending, certified, revoked, expired);
    }

    private CertificationItemDto toItemDto(AccessCertificationItem i) {
        return new CertificationItemDto(i.getId(), i.getCampaignId(), i.getAssignmentId(), i.getUserUuid(),
            i.getUsernameSnapshot(), i.getProfileId(), i.getProfileNameSnapshot(), i.getDecision().name(),
            i.getDecidedBy(), i.getDecidedAt(), i.getComment());
    }
}
