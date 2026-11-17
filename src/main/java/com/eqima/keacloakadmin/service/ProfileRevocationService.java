package com.eqima.keacloakadmin.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.models.entity.ProfileRoleMapping;
import com.eqima.keacloakadmin.models.entity.UserProfileAssignment;
import com.eqima.keacloakadmin.models.request.AssignClientRoleToUserRequest;
import com.eqima.keacloakadmin.models.request.ClientRoleRequest;
import com.eqima.keacloakadmin.repository.ProfileRoleMappingRepository;
import com.eqima.keacloakadmin.repository.UserProfileAssignmentRepository;
import com.eqima.keacloakadmin.util.Details;

import jakarta.ws.rs.NotFoundException;

/**
 * Inverse de GouvernanceService.provisionProfile : retire les rôles client du profil, désactive l'attribution,
 * recalcule le MFA. Appelée uniquement dans un contexte authentifié (AuditService.log lit le JWT).
 */
@Service
public class ProfileRevocationService {

    private final UserProfileAssignmentRepository assignmentRepository;
    private final ProfileRoleMappingRepository mappingRepository;
    private final RoleClientService roleService;
    private final PolicyService policyService;
    private final AuditService auditService;
    private final UserScopeService userScope;

    public ProfileRevocationService(UserProfileAssignmentRepository assignmentRepository,
                                    ProfileRoleMappingRepository mappingRepository,
                                    RoleClientService roleService,
                                    PolicyService policyService,
                                    AuditService auditService,
                                    UserScopeService userScope) {
        this.assignmentRepository = assignmentRepository;
        this.mappingRepository = mappingRepository;
        this.roleService = roleService;
        this.policyService = policyService;
        this.auditService = auditService;
        this.userScope = userScope;
    }

    public void revoke(UserProfileAssignment assignment, String reason) {
        if (!assignment.isActive()) return;

        // Rôles encore exigés par d'autres profils actifs du même utilisateur : on ne les retire pas.
        Set<String> stillRequired = assignmentRepository.findByUserUuidAndActiveTrue(assignment.getUserUuid()).stream()
            .filter(o -> !o.getId().equals(assignment.getId()))
            .flatMap(o -> mappingRepository.findByProfileId(o.getProfileId()).stream())
            .map(ProfileRevocationService::key)
            .collect(Collectors.toSet());

        List<ProfileRoleMapping> toRemove = mappingRepository.findByProfileId(assignment.getProfileId()).stream()
            .filter(m -> !stillRequired.contains(key(m)))
            .toList();

        for (ProfileRoleMapping m : toRemove) {
            try {
                roleService.removeAssignedClientRole(new AssignClientRoleToUserRequest(
                    assignment.getUserUuid(), List.of(new ClientRoleRequest(m.getClientUuid(), m.getRoleName()))));
            } catch (ResourceNotFoundException | NotFoundException e) {
                // rôle, client ou compte déjà disparu : rien à retirer. Toute autre erreur (Keycloak
                // injoignable, 403...) remonte : l'attribution reste active plutôt que "révoquée" à tort.
            }
        }

        assignment.setActive(false);
        assignment.setRevokedAt(Instant.now());
        assignment.setRevokedReason(reason);
        assignmentRepository.save(assignment);

        try {
            policyService.recomputeMfa(assignment.getUserUuid());
        } catch (Exception e) {
            String username = userScope.findUser(assignment.getUserUuid()).get().getUsername();
            auditService.log("RECOMPUTE_MFA_FAILED", assignment.getUserUuid(), username,
                Details.of("assignmentId", assignment.getId(), "error", e.getMessage()));
        }

        String username = userScope.findUser(assignment.getUserUuid()).get().getUsername();
        auditService.log("REVOKE_PROFILE", assignment.getUserUuid(), username,
            Details.of("assignmentId", assignment.getId(), "profileId", assignment.getProfileId(),
                "rolesRemoved", toRemove.size(), "reason", reason));
    }

    /** Cas où Keycloak n'a plus rien à nettoyer (utilisateur ou profil supprimé) : on ferme seulement la ligne locale. */
    public void revokeLocalOnly(UserProfileAssignment assignment, String reason) {
        if (!assignment.isActive()) return;
        assignment.setActive(false);
        assignment.setRevokedAt(Instant.now());
        assignment.setRevokedReason(reason);
        assignmentRepository.save(assignment);
        String username = userScope.findUser(assignment.getUserUuid()).get().getUsername();
        auditService.log("REVOKE_PROFILE_LOCAL", assignment.getUserUuid(), username,
            Details.of("assignmentId", assignment.getId(), "profileId", assignment.getProfileId(), "reason", reason));
    }

    private static String key(ProfileRoleMapping m) {
        return m.getClientUuid() + "|" + m.getRoleName();
    }
}
