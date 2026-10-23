package com.eqima.keacloakadmin.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.models.dto.AccessRequestDto;
import com.eqima.keacloakadmin.models.dto.AccessRequestGateDto;
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
import com.eqima.keacloakadmin.repository.AccessRequestGateRepository;
import com.eqima.keacloakadmin.repository.AccessRequestRepository;
import com.eqima.keacloakadmin.repository.BusinessProfileRepository;
import com.eqima.keacloakadmin.repository.OrganizationRepository;
import com.eqima.keacloakadmin.repository.ProfileRoleMappingRepository;
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
    @Value("${keycloak.realm}")
    private String realmName;

    public GouvernanceService(SecurityUtils securityUtils, 
        AuditService auditService, 
        OrganizationRepository organizationRepository, 
        AccessRequestRepository accessRequestRepository, 
        AccessRequestGateRepository accessRequestGateRepository, 
        Keycloak keycloak, 
        GroupService groupService,
        PolicyService policyService,
        RoleClientService roleService,
        BusinessProfileRepository businessProfileRepository,
        UserProfileAssignmentRepository userProfileAssignmentRepository,
        ProfileRoleMappingRepository roleMappingRepository) {
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
    }

    public AccessRequestDto create(String actorUuid, CreateAccessRequestRequest req) {
        AccessRequest request = new AccessRequest();
        request.setUserUuid(req.userUuid());
        request.setProfileId(req.profileId());
        request.setJustification(req.justification());
        request.setStatus("PENDING");
        AccessRequest saved = accessRequestRepository.save(request);

        // groupes réels du demandeur, résolus via Keycloak — pas via securityUtils (qui lit l'appelant)
        List<GroupRepresentation> requesterGroups = keycloak.realm(realmName)
            .users().get(req.userUuid()).groups();

        for (GroupRepresentation group : requesterGroups) {
            Organization org = organizationRepository.findByKeycloakGroupId(group.getId())
                .orElseThrow(() -> new IllegalStateException("Groupe sans Organization : " + group.getId()));

            AccessRequestGate gate = new AccessRequestGate();
            gate.setAccessRequestId(saved.getId());
            gate.setOrganizationId(org.getId());
            gate.setDecision("PENDING");
            gateRepository.save(gate);
        }

        auditService.log("REQUEST_ACCESS", req.userUuid(), Map.of("accessRequestId", saved.getId().toString(), "gateCount", requesterGroups.size()));
        return toDto(saved);
    }

    public AccessRequestDto approve(String actorUuid, UUID requestId, String comment) {
        AccessRequest request = findRequestOrThrow(requestId);
        List<AccessRequestGate> gates = gateRepository.findByAccessRequestId(requestId);

        if (securityUtils.isSuperAdmin()) {
            gates.stream().filter(g -> g.getDecision().equals("PENDING"))
                .forEach(g -> decide(g, "APPROVED", actorUuid, comment));
        } else if (securityUtils.isAdmin()) {
            Set<UUID> myOrgs = organizationRepository.findByKeycloakGroupIdIn(
                securityUtils.organizations()).stream().map(Organization::getId).collect(Collectors.toSet());

            List<AccessRequestGate> mine = gates.stream()
                .filter(g -> g.getDecision().equals("PENDING") && myOrgs.contains(g.getOrganizationId()))
                .toList();

            if (mine.isEmpty()) {
                throw new AccessDeniedException("Aucun gate de cette demande ne relève de vos organisations.");
            }
            mine.forEach(g -> decide(g, "APPROVED", actorUuid, comment));
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
            request.setStatus("REJECTED");
            request.setDecidedAt(Instant.now());
        } else if (allDecided) {
            SodCheckResult sod = policyService.checkSoD(request.getUserUuid(), request.getProfileId());
            if (sod.result().equals("REJECTED")) {
                request.setStatus("REJECTED");
            } else {
                provisionProfile(request); // assigne les rôles, lignes UserProfileAssignment, recomputeMfa
                request.setStatus("PROVISIONED");
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
            Set<UUID> myOrgIds = organizationRepository.findByKeycloakGroupIdIn(
                securityUtils.organizations()).stream().map(Organization::getId).collect(Collectors.toSet());

            return candidates.stream()
                .filter(req -> !Collections.disjoint(myOrgIds, resolveRequesterOrgs(req.getUserUuid())))
                .map(this::toDto)
                .toList();
        }
        throw new AccessDeniedException("Not allowed to list access requests");
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
            policyService.recomputeMfa(request.getUserUuid()); // acteur système — pas d'admin humain à l'origine de ce recalcul précis
        }

        auditService.log("ASSIGN_PROFILE", null,
            Map.of("accessRequestId", request.getId().toString(), "userUuid", request.getUserUuid(), "profileId", request.getProfileId().toString()));
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
            request.getProfileId(),
            request.getJustification(),
            request.getStatus(),
            request.getCreatedAt(),
            request.getDecidedAt(),
            gateDtos
        );
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

    // renvoie l'ensemble des IDs Organization du demandeur — pas celui de l'appelant courant,
    // donc on interroge Keycloak directement plutôt que SecurityUtils (qui lit le JWT en session)
    private Set<UUID> resolveRequesterOrgs(String requesterUuid) {
        List<GroupRepresentation> groups = keycloak.realm(realmName).users().get(requesterUuid).groups();

        return groups.stream()
            .map(g -> organizationRepository.findByKeycloakGroupId(g.getId())
                .orElseThrow(() -> new IllegalStateException(
                    "Groupe sans Organization associée : " + g.getId() + " (" + g.getName() + ")")))
            .map(Organization::getId)
            .collect(Collectors.toSet());
    }
}
