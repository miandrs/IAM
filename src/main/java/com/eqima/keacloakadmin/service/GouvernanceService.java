package com.eqima.keacloakadmin.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eqima.keacloakadmin.exception.ConflictException;
import com.eqima.keacloakadmin.exception.InvalidRequestException;
import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.models.dto.AccessRequestDto;
import com.eqima.keacloakadmin.models.dto.AccessRequestGateDto;
import com.eqima.keacloakadmin.models.dto.AccessRequestOriginDto;
import com.eqima.keacloakadmin.models.dto.BusinessProfileDto;
import com.eqima.keacloakadmin.models.dto.PagedResult;
import com.eqima.keacloakadmin.models.dto.ProfileRoleMappingDto;
import com.eqima.keacloakadmin.models.dto.SodCheckResult;
import com.eqima.keacloakadmin.models.entity.AccessRequest;
import com.eqima.keacloakadmin.models.entity.AccessRequestGate;
import com.eqima.keacloakadmin.models.entity.AccessRequestStatus;
import com.eqima.keacloakadmin.models.entity.BusinessProfile;
import com.eqima.keacloakadmin.models.entity.Organization;
import com.eqima.keacloakadmin.models.entity.ProfileRoleMapping;
import com.eqima.keacloakadmin.models.entity.UserProfileAssignment;
import com.eqima.keacloakadmin.models.request.AssignClientRoleToUserRequest;
import com.eqima.keacloakadmin.models.request.ClientRoleRequest;
import com.eqima.keacloakadmin.models.request.CreateAccessRequestRequest;
import com.eqima.keacloakadmin.models.request.CreateBusinessProfileRequest;
import com.eqima.keacloakadmin.models.request.CreateRoleMappingRequest;
import com.eqima.keacloakadmin.models.request.UpdateBusinessProfileRequest;
import com.eqima.keacloakadmin.repository.AccessRequestGateRepository;
import com.eqima.keacloakadmin.repository.AccessRequestRepository;
import com.eqima.keacloakadmin.repository.AccessRequestSpecifications;
import com.eqima.keacloakadmin.repository.BusinessProfileRepository;
import com.eqima.keacloakadmin.repository.OrganizationRepository;
import com.eqima.keacloakadmin.repository.ProfileRoleMappingRepository;
import com.eqima.keacloakadmin.repository.SodRuleRepository;
import com.eqima.keacloakadmin.repository.UserProfileAssignmentRepository;
import com.eqima.keacloakadmin.util.SecurityUtils;

@Service 
public class GouvernanceService {
    private final SecurityUtils securityUtils;
    private  final AuditService auditService;
    private final OrganizationRepository organizationRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final AccessRequestGateRepository gateRepository;
    private final Keycloak keycloak;
    private final GroupService groupService;
    private final PolicyService policyService;
    private  final RoleClientService roleService;
    private final BusinessProfileRepository businessProfileRepository;
    private final UserProfileAssignmentRepository userProfileAssignmentRepository;
    private final ProfileRoleMappingRepository roleMappingRepository;
    private final UserScopeService userScope;
    private final SodRuleRepository sodRuleRepository;
    @Value("${keycloak.realm}")
    private String realmName;

    private final ProfileVisibilityService visibility;

    private final AssignmentGuardService assignmentGuard;

    private static final String SA_PREFIX = "[SA] ";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public GouvernanceService(SecurityUtils securityUtils, 
        AuditService auditService, 
        OrganizationRepository organizationRepository, 
        AccessRequestRepository accessRequestRepository, 
        AccessRequestGateRepository accessRequestGateRepository, 
        @Qualifier("keycloakClient") Keycloak keycloak, 
        GroupService groupService,
        PolicyService policyService,
        RoleClientService roleService,
        BusinessProfileRepository businessProfileRepository,
        UserProfileAssignmentRepository userProfileAssignmentRepository,
        ProfileRoleMappingRepository roleMappingRepository,
        UserScopeService userScope,
        SodRuleRepository sodRuleRepository,
        ProfileVisibilityService visibility,
        AssignmentGuardService assignmentGuard) {
        this.securityUtils = securityUtils;
        this.auditService = auditService;
        this.organizationRepository = organizationRepository;
        this.accessRequestRepository = accessRequestRepository;
        this.gateRepository = accessRequestGateRepository;
        this.keycloak = keycloak;
        this.groupService = groupService;
        this.policyService = policyService;
        this.roleService = roleService;
        this.businessProfileRepository = businessProfileRepository;
        this.userProfileAssignmentRepository = userProfileAssignmentRepository;
        this.roleMappingRepository = roleMappingRepository;
        this.userScope = userScope;
        this.sodRuleRepository = sodRuleRepository;
        this.visibility = visibility;
        this.assignmentGuard = assignmentGuard;
    }

    @Transactional
    public AccessRequestDto create(CreateAccessRequestRequest req, String clientIp) {
        String me = securityUtils.currentUserId();
        if (!me.equals(req.userUuid())) {
            // demande pour un tiers : réservé à un ADMIN / SUPERADMIN qui gère ce compte
            userScope.assertCanManage(req.userUuid());
        }
        // findProfileOrThrow(req.profileId());
        findVisibleProfileOrThrow(req.profileId()); // 404 propre si le profil n'existe pas ou n'est pas visible
        if (!userScope.userExists(req.userUuid())) {
            throw new ResourceNotFoundException("Utilisateur introuvable : " + req.userUuid());
        }

        assignmentGuard.assertAllowed(req.userUuid(), req.profileId(), null); // profils puis rôles

        // Organisations racines du demandeur (sous-groupes inclus), résolues AVANT toute écriture : avant, un groupe
        // sans Organization levait une exception après l'enregistrement de la demande, qui restait en base sans gate.
        Set<UUID> orgIds = userScope.organizationIdsOfUser(req.userUuid(), userScope.topLevelGroupIdsByName());
        if (orgIds.isEmpty()) {
            throw new ConflictException("Le demandeur n'appartient à aucune organisation suivie : demande impossible.");
        }

        AccessRequest request = new AccessRequest();
        request.setUserUuid(req.userUuid());
        request.setProfileId(req.profileId());
        request.setJustification(req.justification());
        request.setStatus(AccessRequestStatus.PENDING);
        request.setCreatedAt(Instant.now());
        // traçabilité : qui a soumis, depuis quelle IP, et l'origine (tenants / produits / rôles) figée à cet instant
        request.setRequestedBy(me);
        request.setRequestedByUsername(securityUtils.username());
        request.setRequesterIp(clientIp);
        request.setOrigin(MAPPER.convertValue(buildOrigin(req.profileId(), orgIds), new TypeReference<Map<String, Object>>() {}));
        AccessRequest saved = accessRequestRepository.save(request);

        for (UUID orgId : orgIds) {
            AccessRequestGate gate = new AccessRequestGate();
            gate.setAccessRequestId(saved.getId());
            gate.setOrganizationId(orgId);
            gate.setDecision("PENDING");
            gateRepository.save(gate);
        }

        UserRepresentation user = userScope.findUser(req.userUuid()).get();
        auditService.log("REQUEST_ACCESS", req.userUuid(), user.getUsername(),
            auditDetails(clientIp, "accessRequestId", saved.getId().toString(), "profileId", req.profileId().toString(),
                "gateCount", orgIds.size(), "requestedBy", me));
        return toDto(saved);
    }

    @Transactional
    public AccessRequestDto approve(UUID requestId, String comment, String clientIp) {
        AccessRequest request = findRequestOrThrow(requestId);
        requireDecidable(request);
        assignmentGuard.assertAllowed(request.getUserUuid(), request.getProfileId(), null); // profils puis rôles
        List<AccessRequestGate> gates = gateRepository.findByAccessRequestId(requestId);

        if (securityUtils.isSuperAdmin()) {
            gates.stream().filter(g -> g.getDecision().equals("PENDING"))
                .forEach(g -> decide(g, "APPROVED", securityUtils.currentUserId(), comment, clientIp));
        } else if (securityUtils.isAdmin()) {
            Set<UUID> myOrgs = currentApproverOrgIds();

            List<AccessRequestGate> mine = gates.stream()
                .filter(g -> g.getDecision().equals("PENDING") && myOrgs.contains(g.getOrganizationId()))
                .toList();

            if (mine.isEmpty()) {
                throw new AccessDeniedException("Aucun gate de cette demande ne relève de vos organisations.");
            }
            mine.forEach(g -> decide(g, "APPROVED", securityUtils.currentUserId(), comment, clientIp));
        } else {
            throw new AccessDeniedException("Not allowed to approve access requests");
        }

        userScope.findUser(request.getUserUuid()).ifPresent(user ->
            auditService.log("APPROVE_ACCESS_REQUEST", request.getUserUuid(), user.getUsername(),
                auditDetails(clientIp, "accessRequestId", requestId.toString(), "comment", comment)));

        return finalizeIfComplete(request, gates);
    }

    private void decide(AccessRequestGate gate, String decision, String actorUuid, String comment, String clientIp) {
        gate.setDecision(decision);
        gate.setApproverUuid(actorUuid);
        gate.setComment(comment);
        gate.setApproverIp(clientIp);
        gate.setDecidedAt(Instant.now());
        gateRepository.save(gate);
    }

    // "sinon il n'a pas accès à tous" — rejet unanime : un seul gate rejeté suffit à tout rejeter,
    // pas d'accès partiel aux role-mappings du profil
    private AccessRequestDto finalizeIfComplete(AccessRequest request, List<AccessRequestGate> gates) {
        boolean anyRejected = gates.stream().anyMatch(g -> g.getDecision().equals("REJECTED"));
        boolean allDecided = gates.stream().noneMatch(g -> g.getDecision().equals("PENDING"));

        if (anyRejected) {
            request.setStatus(AccessRequestStatus.REJECTED);
            request.setDecidedAt(Instant.now());
        } else if (allDecided) {
            SodCheckResult sod = assignmentGuard.check(request.getUserUuid(), request.getProfileId(), null); // profils puis rôles, avant tout provisionnement
            
            if (sod.result().equals("REJECTED")) {
                request.setStatus(AccessRequestStatus.REJECTED);
            } else {
                provisionProfile(request); // assigne les rôles, lignes UserProfileAssignment, recomputeMfa
                request.setStatus(AccessRequestStatus.PROVISIONED);
            }
            request.setDecidedAt(Instant.now());
        }
        // sinon : encore des gates PENDING, on attend les autres groupes — statut reste PENDING

        return toDto(accessRequestRepository.save(request));
    }

    public List<AccessRequestDto> listPendingForCurrentApprover() {
        List<AccessRequest> candidates = accessRequestRepository.findByStatus(AccessRequestStatus.PENDING);

        if (securityUtils.isSuperAdmin()) {
            return candidates.stream().map(this::toDto).toList();
        }

        if (securityUtils.isAdmin()) {
            
            Set<UUID> myOrgIds = currentApproverOrgIds();

            if (myOrgIds.isEmpty()) return List.of();

            Map<String, String> topGroups = userScope.topLevelGroupIdsByName();
            return candidates.stream()
                .filter(req -> !Collections.disjoint(myOrgIds, userScope.organizationIdsOfUser(req.getUserUuid(), topGroups)))
                .map(this::toDto)
                .toList();
        }

        throw new AccessDeniedException("Not allowed to list access requests");
    }

    public AccessRequestDto reject(UUID requestId, String comment, String clientIp) {
        AccessRequest request = findRequestOrThrow(requestId);
        requireDecidable(request);
        List<AccessRequestGate> gates = gateRepository.findByAccessRequestId(requestId);

        List<AccessRequestGate> myPendingGates;
        if (securityUtils.isSuperAdmin()) {
            myPendingGates = gates.stream().filter(g -> g.getDecision().equals("PENDING")).toList();
        } else if (securityUtils.isAdmin()) {
            Set<UUID> myOrgs = currentApproverOrgIds();
            myPendingGates = gates.stream()
                .filter(g -> g.getDecision().equals("PENDING") && myOrgs.contains(g.getOrganizationId()))
                .toList();
            if (myPendingGates.isEmpty()) {
                throw new AccessDeniedException("Aucun gate de cette demande ne relève de vos organisations.");
            }
        } else {
            throw new AccessDeniedException("Not allowed to reject access requests");
        }

        myPendingGates.forEach(g -> decide(g, "REJECTED", securityUtils.currentUserId(), comment, clientIp));

        request.setStatus(AccessRequestStatus.REJECTED);
        request.setDecidedAt(Instant.now());
        accessRequestRepository.save(request);

        userScope.findUser(request.getUserUuid()).ifPresent(user -> {
            auditService.log("REJECT_ACCESS_REQUEST", request.getUserUuid(), user.getUsername(),
                auditDetails(clientIp, "accessRequestId", requestId.toString(), "comment", comment));
        });

        return toDto(request, gates);
    }

    public AccessRequestDto getById(UUID requestId) {
        AccessRequest request = findRequestOrThrow(requestId);
        // même règle de visibilité que l'historique ; 404 (et non 403) pour ne pas révéler l'existence de la demande
        if (!accessRequestRepository.exists(visibleSpec().and((root, query, cb) -> cb.equal(root.get("id"), requestId)))) {
            throw new ResourceNotFoundException("Demande d'accès introuvable : " + requestId);
        }
        return toDto(request);
    }

    // ═══════════════ Historique ═══════════════

    // SUPERADMIN : tout ; ADMIN : demandes de ses organisations + les siennes ; utilisateur simple : les siennes
    private Specification<AccessRequest> visibleSpec() {
        if (securityUtils.isSuperAdmin()) return (root, query, cb) -> cb.conjunction(); // aucune restriction
        String me = securityUtils.currentUserId();
        Set<UUID> orgIds = securityUtils.isAdmin() ? currentApproverOrgIds() : Set.of();
        return AccessRequestSpecifications.visibleTo(me, orgIds);
    }

    public PagedResult<AccessRequestDto> history(AccessRequestStatus status, String userUuid, UUID profileId,
                                                 Instant from, Instant to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("La date de début doit précéder la date de fin.");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Specification<AccessRequest> spec = visibleSpec()
            .and(AccessRequestSpecifications.hasStatus(status))
            .and(AccessRequestSpecifications.forUser(userUuid))
            .and(AccessRequestSpecifications.forProfile(profileId))
            .and(AccessRequestSpecifications.createdFrom(from))
            .and(AccessRequestSpecifications.createdTo(to));

        Page<AccessRequest> result = accessRequestRepository.findAll(spec,
            PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        // un seul aller-retour pour les gates de la page, et des caches pour les appels Keycloak répétés
        List<UUID> ids = result.getContent().stream().map(AccessRequest::getId).toList();
        Map<UUID, List<AccessRequestGate>> gatesByRequest = ids.isEmpty() ? Map.of()
            : gateRepository.findByAccessRequestIdIn(ids).stream()
                .collect(Collectors.groupingBy(AccessRequestGate::getAccessRequestId));
        Lookup lookup = new Lookup();

        List<AccessRequestDto> content = result.getContent().stream()
            .map(r -> toDto(r, gatesByRequest.getOrDefault(r.getId(), List.of()), lookup))
            .toList();

        return new PagedResult<>(content, result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages());
    }

    // IDs Organization de l'appelant courant (ADMIN) : noms du token -> groupes Keycloak -> Organization
    private Set<UUID> currentApproverOrgIds() {
        // List<String> myGroupIds = groupService.getAllGroups().stream()
        //     .map(GroupRepresentation::getId)
        //     .toList();

        // if (myGroupIds.isEmpty()) {
        //     return Set.of();
        // }

        // return organizationRepository.findByKeycloakGroupIdIn(myGroupIds).stream()
        //     .map(Organization::getId)
        //     .collect(Collectors.toSet());
        return userScope.callerOrganizationIds();
    }

    // ═══════════════ Business Profiles ═══════════════

    public List<BusinessProfileDto> listProfiles() {
        var visible = visibility.visibilityFilter();
        return businessProfileRepository.findAll().stream().filter(visible).map(this::toProfileDto).toList();
    }

    public BusinessProfileDto getProfile(UUID id) {
        return toProfileDto(findVisibleProfileOrThrow(id));
    }

    public BusinessProfileDto createProfile(CreateBusinessProfileRequest req) {
        requireProfileManager();
        boolean sa = securityUtils.isSuperAdmin();
        BusinessProfile profile = new BusinessProfile();
        profile.setName(sa ? withSaPrefix(req.name()) : req.name());
        profile.setOrganizationId(req.organizationId());
        profile.setMfaRequired(req.mfaRequired());
        // jamais lus dans la requête : toujours dérivés de l'utilisateur connecté
        profile.setCreatedBy(securityUtils.currentUserId());
        profile.setOwnerRole(securityUtils.isSuperAdmin()
            ? ProfileVisibilityService.OWNER_SUPERADMIN : ProfileVisibilityService.OWNER_ADMIN);
        BusinessProfile saved = businessProfileRepository.save(profile);

        auditService.log("CREATE_PROFILE", null, null, Map.of("profileId", saved.getId().toString(), "name", saved.getName()));
        return toProfileDto(saved);
    }

    public BusinessProfileDto updateProfile(UUID id, UpdateBusinessProfileRequest req) {
        requireProfileManager();
        BusinessProfile profile = findVisibleProfileOrThrow(id);
        if (req.name() != null) {
            boolean saProfile = ProfileVisibilityService.OWNER_SUPERADMIN.equals(profile.getOwnerRole());
            profile.setName(saProfile ? withSaPrefix(req.name()) : stripSaPrefix(req.name()));
        }
        if (req.mfaRequired() != null) profile.setMfaRequired(req.mfaRequired());
        BusinessProfile saved = businessProfileRepository.save(profile);

        userScope.findUser(String.valueOf(id)).ifPresent(user -> {
            auditService.log("UPDATE_PROFILE", String.valueOf(id), user.getUsername(), Map.of("profileId", id.toString()));
        });
        return toProfileDto(saved);
    }

    @Transactional
    public void deleteProfile(UUID id) {
        requireProfileManager();
        findVisibleProfileOrThrow(id);
        if (userProfileAssignmentRepository.existsByProfileIdAndActiveTrue(id)) {
            throw new ConflictException("Ce profil est attribué à des utilisateurs : révoquez d'abord les attributions.");
        }
        if (accessRequestRepository.existsByProfileIdAndStatus(id, AccessRequestStatus.PENDING)) {
            throw new ConflictException("Des demandes d'accès en attente visent ce profil : traitez-les d'abord.");
        }
        // plus de lignes orphelines : mappings de rôles et règles SoD qui référencent ce profil
        roleMappingRepository.deleteAll(roleMappingRepository.findByProfileId(id));
        sodRuleRepository.deleteAll(sodRuleRepository.findAllInvolvingProfile(id));
        businessProfileRepository.deleteById(id);
        userScope.findUser(String.valueOf(id)).ifPresent(user -> {
            auditService.log("DELETE_PROFILE", String.valueOf(id), user.getUsername(), Map.of("profileId", id.toString()));
        });
    }

    private BusinessProfile findVisibleProfileOrThrow(UUID id) {
        return visibility.requireVisible(findProfileOrThrow(id));
    }

    private void requireProfileManager() {
        if (!securityUtils.isAdmin() && !securityUtils.isSuperAdmin()) {
            throw new AccessDeniedException("Réservé aux ADMIN et SUPERADMIN.");
        }
    }

    private BusinessProfile findProfileOrThrow(UUID id) {
        return businessProfileRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Profil introuvable : " + id));
    }

    private BusinessProfileDto toProfileDto(BusinessProfile p) {
        return new BusinessProfileDto(p.getId(), p.getName(), p.getOrganizationId(), p.isMfaRequired());
    }

    // ═══════════════ Role mappings ═══════════════

    public List<ProfileRoleMappingDto> listRoleMappings(UUID profileId) {
        findVisibleProfileOrThrow(profileId);
        return roleMappingRepository.findByProfileId(profileId).stream().map(this::toMappingDto).toList();

    }

    public ProfileRoleMappingDto createRoleMapping(UUID profileId, CreateRoleMappingRequest req) {
        requireProfileManager();
        findVisibleProfileOrThrow(profileId); // 404 propre si le profil n'existe pas
        roleService.assertClientAssignable(req.clientUuid());

        ProfileRoleMapping mapping = new ProfileRoleMapping();
        mapping.setProfileId(profileId);
        mapping.setClientUuid(req.clientUuid());
        mapping.setRoleName(req.roleName());
        ProfileRoleMapping saved = roleMappingRepository.save(mapping);

        auditService.log("CREATE_ROLE_MAPPING", null, null,
            Map.of("profileId", profileId.toString(), "clientUuid", req.clientUuid(), "roleName", req.roleName()));
        return toMappingDto(saved);
    }

    public void deleteRoleMapping(UUID mappingId) {
        requireProfileManager();

        ProfileRoleMapping mapping = roleMappingRepository.findById(mappingId)
            .orElseThrow(() -> new ResourceNotFoundException("Mapping introuvable : " + mappingId));

        findVisibleProfileOrThrow(mapping.getProfileId()); // 404 propre si le profil associé n'est pas visible
        roleMappingRepository.deleteById(mappingId);

        auditService.log("DELETE_ROLE_MAPPING", null, null,
            Map.of("mappingId", mappingId.toString(), "profileId", mapping.getProfileId().toString()));
    }

    private ProfileRoleMappingDto toMappingDto(ProfileRoleMapping m) {
        return new ProfileRoleMappingDto(m.getId(), m.getProfileId(), m.getClientUuid(), m.getRoleName());
    }

    private void provisionProfile(AccessRequest request) {
        List<ProfileRoleMapping> mappings = roleMappingRepository.findByProfileId(request.getProfileId());

        for (ProfileRoleMapping mapping : mappings) {
            AssignClientRoleToUserRequest roleReq = new AssignClientRoleToUserRequest(
                request.getUserUuid(),
                List.of(new ClientRoleRequest(mapping.getClientUuid(), mapping.getRoleName()))
            );
            roleService.assignClientRoles(roleReq); // réutilise l'endpoint déjà existant et testé
        }

        UserProfileAssignment assignment = new UserProfileAssignment();
        assignment.setUserUuid(request.getUserUuid());
        assignment.setProfileId(request.getProfileId());
        assignment.setActive(true);
        userProfileAssignmentRepository.save(assignment);

        BusinessProfile profile = businessProfileRepository.findById(request.getProfileId()).orElseThrow();
        if (profile.isMfaRequired()) {
            try {
                policyService.recomputeMfa(request.getUserUuid());
            } catch (Exception e) {
                userScope.findUser(request.getUserUuid()).ifPresent(user -> {
                    auditService.log("RECOMPUTE_MFA_FAILED", request.getUserUuid(), user.getUsername(),
                        Map.of("accessRequestId", request.getId().toString(), "error", String.valueOf(e.getMessage())));
                });
                
            }
        }

        userScope.findUser(request.getUserUuid()).ifPresent(user -> {
            auditService.log("ASSIGN_PROFILE", request.getUserUuid(), user.getUsername(),
                Map.of("accessRequestId", request.getId().toString(), "profileId", request.getProfileId().toString()));
        });
        
    }

    // Une demande déjà PROVISIONED / REJECTED ne se rejoue pas (sinon une ré-approbation, vue comme "profil déjà actif"
    // par le SoD, faisait passer une demande provisionnée à REJECTED). Et on ne décide jamais de sa propre demande.
    private void requireDecidable(AccessRequest request) {
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new ConflictException("La demande est déjà traitée : " + request.getStatus());
        }
        if (securityUtils.currentUserId().equals(request.getUserUuid())) {
            throw new AccessDeniedException("Vous ne pouvez pas décider de votre propre demande.");
        }
    }

    private AccessRequest findRequestOrThrow(UUID requestId) {
        return accessRequestRepository.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Demande d'accès introuvable : " + requestId));
    }

    private AccessRequestDto toDto(AccessRequest request) {
        return toDto(request, gateRepository.findByAccessRequestId(request.getId()));
    }

    // noms déjà résolus pendant un même appel (évite de rappeler Keycloak pour chaque ligne d'une page)
    private static final class Lookup {
        final Map<String, String> users = new HashMap<>();
        final Map<UUID, String> organizations = new HashMap<>();
    }

    private AccessRequestDto toDto(AccessRequest request, List<AccessRequestGate> gates) {
        return toDto(request, gates, new Lookup());
    }

    private AccessRequestDto toDto(AccessRequest request, List<AccessRequestGate> gates, Lookup lookup) {
        List<AccessRequestGateDto> gateDtos = gates.stream()
            .map(g -> toGateDto(g, lookup))
            .toList();

        return new AccessRequestDto(
            request.getId(),
            request.getUserUuid(),
            lookup.users.computeIfAbsent(request.getUserUuid(), this::resolveUsernameSafe),
            request.getProfileId(),
            resolveProfileNameSafe(request.getProfileId()),
            request.getJustification(),
            String.valueOf(request.getStatus()),
            request.getCreatedAt(),
            request.getDecidedAt(),
            request.getRequestedBy(),
            request.getRequestedByUsername(),
            request.getRequesterIp(),
            originOf(request, gates, lookup),
            gateDtos
        );
    }

    private String resolveUsernameSafe(String userUuid) {
        try {
            return keycloak.realm(realmName).users().get(userUuid).toRepresentation().getUsername();
        } catch (Exception e) {
            return "(utilisateur introuvable)";
        }
    }

    private String resolveProfileNameSafe(UUID profileId) {
        return businessProfileRepository.findById(profileId)
            .map(BusinessProfile::getName)
            .orElse("(profil introuvable)");
    }

    private AccessRequestGateDto toGateDto(AccessRequestGate gate, Lookup lookup) {
        return new AccessRequestGateDto(
            gate.getOrganizationId(),
            lookup.organizations.computeIfAbsent(gate.getOrganizationId(), this::organizationName),
            gate.getDecision(),
            gate.getApproverUuid(),
            gate.getComment(),
            gate.getDecidedAt(),
            gate.getApproverIp()
        );
    }

    private String organizationName(UUID organizationId) {
        return organizationRepository.findById(organizationId)
            .map(org -> resolveGroupNameSafe(org.getKeycloakGroupId()))
            .orElse("(organisation introuvable)");
    }

    // ═══════════════ Origine & traçabilité ═══════════════

    /**
     * Origine d'une demande : organisations (tenants) du demandeur, profil, produits (clients Keycloak)
     * et rôles visés par le profil. Calculée à la création et stockée telle quelle.
     */
    private AccessRequestOriginDto buildOrigin(UUID profileId, Collection<UUID> orgIds) {
        return buildOrigin(profileId, orgIds, new Lookup());
    }

    private AccessRequestOriginDto buildOrigin(UUID profileId, Collection<UUID> orgIds, Lookup lookup) {
        List<AccessRequestOriginDto.Organization> orgs = orgIds.stream()
            .map(id -> new AccessRequestOriginDto.Organization(id, lookup.organizations.computeIfAbsent(id, this::organizationName)))
            .toList();

        List<ProfileRoleMapping> mappings = roleMappingRepository.findByProfileId(profileId);
        Map<String, String> clientIds = new LinkedHashMap<>();
        for (ProfileRoleMapping m : mappings) {
            clientIds.computeIfAbsent(m.getClientUuid(), this::resolveClientIdSafe);
        }

        return new AccessRequestOriginDto(
            orgs,
            new AccessRequestOriginDto.Profile(profileId, resolveProfileNameSafe(profileId)),
            clientIds.entrySet().stream()
                .map(e -> new AccessRequestOriginDto.Product(e.getKey(), e.getValue())).toList(),
            mappings.stream()
                .map(m -> new AccessRequestOriginDto.Role(m.getClientUuid(), clientIds.get(m.getClientUuid()), m.getRoleName()))
                .toList()
        );
    }

    // instantané stocké si présent ; sinon (demandes antérieures à la traçabilité) on reconstitue l'état actuel
    private AccessRequestOriginDto originOf(AccessRequest request, List<AccessRequestGate> gates, Lookup lookup) {
        Map<String, Object> snapshot = request.getOrigin();
        if (snapshot != null && !snapshot.isEmpty()) {
            try {
                return MAPPER.convertValue(snapshot, AccessRequestOriginDto.class);
            } catch (IllegalArgumentException ignored) {
                // instantané illisible : repli sur l'état actuel
            }
        }
        return buildOrigin(request.getProfileId(),
            gates.stream().map(AccessRequestGate::getOrganizationId).distinct().toList(), lookup);
    }

    private String resolveClientIdSafe(String clientUuid) {
        try {
            return keycloak.realm(realmName).clients().get(clientUuid).toRepresentation().getClientId();
        } catch (Exception e) {
            return clientUuid;
        }
    }

    // détails d'audit (clé, valeur, clé, valeur…) + IP ; les valeurs null sont omises (Map.of les refuse)
    private static Map<String, Object> auditDetails(String clientIp, Object... keyValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) details.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        if (clientIp != null) details.put("ip", clientIp);
        return details;
    }

    // même repli défensif que OrganizationService.resolveGroupName() — un groupe Keycloak
    // peut avoir disparu après coup sans que l'Organization locale ait été nettoyée
    private String resolveGroupNameSafe(String keycloakGroupId) {
        try {
            return groupService.getGroupById(keycloakGroupId).getName();
        } catch (Exception e) {
            return "(groupe Keycloak introuvable)";
        }
    }

    private static String withSaPrefix(String name) {
        String n = name.strip();
        return n.startsWith(SA_PREFIX) ? n : SA_PREFIX + n;
    }

    // un ADMIN ne doit pas pouvoir se faire passer pour un profil SUPERADMIN
    private static String stripSaPrefix(String name) {
        String n = name.strip();
        return n.startsWith(SA_PREFIX) ? n.substring(SA_PREFIX.length()).strip() : n;
    }
}
