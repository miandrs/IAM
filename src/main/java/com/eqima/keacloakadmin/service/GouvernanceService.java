package com.eqima.keacloakadmin.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eqima.keacloakadmin.exception.ConflictException;
import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.models.dto.AccessRequestDto;
import com.eqima.keacloakadmin.models.dto.AccessRequestGateDto;
import com.eqima.keacloakadmin.models.dto.BusinessProfileDto;
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

    private static final String SA_PREFIX = "[SA] ";

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
        ProfileVisibilityService visibility) {
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
    }

    @Transactional
    public AccessRequestDto create(CreateAccessRequestRequest req) {
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

        policyService.assertSoDAllowed(req.userUuid(), req.profileId());  

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
        AccessRequest saved = accessRequestRepository.save(request);

        for (UUID orgId : orgIds) {
            AccessRequestGate gate = new AccessRequestGate();
            gate.setAccessRequestId(saved.getId());
            gate.setOrganizationId(orgId);
            gate.setDecision("PENDING");
            gateRepository.save(gate);
        }

        auditService.log("REQUEST_ACCESS", req.userUuid(), Map.of("accessRequestId", saved.getId().toString(), "gateCount", orgIds.size()));
        return toDto(saved);
    }

    @Transactional
    public AccessRequestDto approve(UUID requestId, String comment) {
        AccessRequest request = findRequestOrThrow(requestId);
        requireDecidable(request);
        policyService.assertSoDAllowed(request.getUserUuid(), request.getProfileId());
        List<AccessRequestGate> gates = gateRepository.findByAccessRequestId(requestId);

        if (securityUtils.isSuperAdmin()) {
            gates.stream().filter(g -> g.getDecision().equals("PENDING"))
                .forEach(g -> decide(g, "APPROVED", securityUtils.currentUserId(), comment));
        } else if (securityUtils.isAdmin()) {
            Set<UUID> myOrgs = currentApproverOrgIds();

            List<AccessRequestGate> mine = gates.stream()
                .filter(g -> g.getDecision().equals("PENDING") && myOrgs.contains(g.getOrganizationId()))
                .toList();

            if (mine.isEmpty()) {
                throw new AccessDeniedException("Aucun gate de cette demande ne relève de vos organisations.");
            }
            mine.forEach(g -> decide(g, "APPROVED", securityUtils.currentUserId(), comment));
        } else {
            throw new AccessDeniedException("Not allowed to approve access requests");
        }

        return finalizeIfComplete(request, gates);
    }

    private void decide(AccessRequestGate gate, String decision, String actorUuid, String comment) {
        gate.setDecision(decision);
        gate.setApproverUuid(actorUuid);
        gate.setComment(comment);
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
            SodCheckResult sod = policyService.checkSoD(request.getUserUuid(), request.getProfileId());
            
            if (sod.result().equals("REJECTED")) {
                request.setStatus(AccessRequestStatus.REJECTED);
            } else {
                policyService.assertSoDAllowed(request.getUserUuid(), request.getProfileId());
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

    public AccessRequestDto reject(UUID requestId, String comment) {
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

        myPendingGates.forEach(g -> decide(g, "REJECTED", securityUtils.currentUserId(), comment));

        request.setStatus(AccessRequestStatus.REJECTED);
        request.setDecidedAt(Instant.now());
        accessRequestRepository.save(request);

        auditService.log("REJECT_ACCESS_REQUEST", request.getUserUuid(),
            Map.of("accessRequestId", requestId.toString(), "comment", comment));

        return toDto(request, gates);
    }

    public AccessRequestDto getById(UUID requestId) {
        return toDto(findRequestOrThrow(requestId));
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

        auditService.log("CREATE_PROFILE", String.valueOf(profile.getId()),
            Map.of("profileId", saved.getId().toString(), "name", saved.getName()));
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

        auditService.log("UPDATE_PROFILE", String.valueOf(id), Map.of("profileId", id.toString()));
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
        auditService.log("DELETE_PROFILE", String.valueOf(id), Map.of("profileId", id.toString()));
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

        auditService.log("CREATE_ROLE_MAPPING", String.valueOf(profileId),
            Map.of("profileId", profileId.toString(), "clientUuid", req.clientUuid(), "roleName", req.roleName()));
        return toMappingDto(saved);
    }

    public void deleteRoleMapping(UUID mappingId) {
        requireProfileManager();

        ProfileRoleMapping mapping = roleMappingRepository.findById(mappingId)
            .orElseThrow(() -> new ResourceNotFoundException("Mapping introuvable : " + mappingId));

        findVisibleProfileOrThrow(mapping.getProfileId()); // 404 propre si le profil associé n'est pas visible
        roleMappingRepository.deleteById(mappingId);

        auditService.log("DELETE_ROLE_MAPPING", String.valueOf(mappingId),
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
                auditService.log("RECOMPUTE_MFA_FAILED", request.getUserUuid(),
                    Map.of("accessRequestId", request.getId().toString(), "error", String.valueOf(e.getMessage())));
            }
        }

        auditService.log("ASSIGN_PROFILE", String.valueOf(profile.getId()),
            Map.of("accessRequestId", request.getId().toString(), "userUuid", request.getUserUuid(), "profileId", request.getProfileId().toString()));
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

    private AccessRequestDto toDto(AccessRequest request, List<AccessRequestGate> gates) {
        List<AccessRequestGateDto> gateDtos = gates.stream()
            .map(this::toGateDto)
            .toList();

        return new AccessRequestDto(
            request.getId(),
            request.getUserUuid(),
            resolveUsernameSafe(request.getUserUuid()),
            request.getProfileId(),
            resolveProfileNameSafe(request.getProfileId()),
            request.getJustification(),
            String.valueOf(request.getStatus()),
            request.getCreatedAt(),
            request.getDecidedAt(),
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

    private AccessRequestGateDto toGateDto(AccessRequestGate gate) {
        String orgName = organizationRepository.findById(gate.getOrganizationId())
            .map(org -> resolveGroupNameSafe(org.getKeycloakGroupId()))
            .orElse("(organisation introuvable)");

        return new AccessRequestGateDto(
            gate.getOrganizationId(),
            orgName,
            gate.getDecision(),
            gate.getApproverUuid(),
            gate.getComment()
        );
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
