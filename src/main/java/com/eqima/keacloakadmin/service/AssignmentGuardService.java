package com.eqima.keacloakadmin.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.exception.ConflictException;
import com.eqima.keacloakadmin.models.dto.SodCheckResult;
import com.eqima.keacloakadmin.models.request.ClientRoleRequest;
import com.eqima.keacloakadmin.repository.ProfileRoleMappingRepository;

/**
 * Point d'entrée unique du contrôle SoD AVANT toute attribution (profil ou rôles). Ne modifie rien.
 * Ordre : 1) SoD de profils, 2) SoD de rôles sur l'ensemble des rôles qui seraient attribués
 * (rôles du profil demandé + rôles explicitement demandés).
 */
@Service
public class AssignmentGuardService {

    private final PolicyService policyService;
    private final RoleSodService roleSodService;
    private final ProfileRoleMappingRepository roleMappingRepository;

    public AssignmentGuardService(PolicyService policyService,
                                  RoleSodService roleSodService,
                                  ProfileRoleMappingRepository roleMappingRepository) {
        this.policyService = policyService;
        this.roleSodService = roleSodService;
        this.roleMappingRepository = roleMappingRepository;
    }

    /**
     * @param profileId  profil à attribuer, ou null si on n'attribue que des rôles
     * @param extraRoles rôles à attribuer directement, ou null
     */
    public SodCheckResult check(String userUuid, UUID profileId, List<ClientRoleRequest> extraRoles) {
        // 1) profils
        if (profileId != null) {
            SodCheckResult profileCheck = policyService.checkSoD(userUuid, profileId);
            if (!"APPROVED".equals(profileCheck.result())) return profileCheck;
        }

        // 2) rôles : tout ce qui serait réellement attribué
        List<ClientRoleRequest> roles = new ArrayList<>();
        if (profileId != null) {
            roleMappingRepository.findByProfileId(profileId)
                .forEach(m -> roles.add(new ClientRoleRequest(m.getClientUuid(), m.getRoleName())));
        }
        if (extraRoles != null) roles.addAll(extraRoles);
        return roleSodService.checkRoles(userUuid, roles);
    }

    public void assertAllowed(String userUuid, UUID profileId, List<ClientRoleRequest> extraRoles) {
        SodCheckResult r = check(userUuid, profileId, extraRoles);
        if (!"APPROVED".equals(r.result())) {
            throw new ConflictException(r.reason());
        }
    }
}
