package com.eqima.keacloakadmin.service;

import com.eqima.keacloakadmin.dto.CreateSodRuleRequest;
import com.eqima.keacloakadmin.dto.MfaRecomputeResult;
import com.eqima.keacloakadmin.dto.SodCheckResult;
import com.eqima.keacloakadmin.entity.BusinessProfile;
import com.eqima.keacloakadmin.entity.SodRule;
import com.eqima.keacloakadmin.entity.UserProfileAssignment;
import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.repository.BusinessProfileRepository;
import com.eqima.keacloakadmin.repository.SodRuleRepository;
import com.eqima.keacloakadmin.repository.UserProfileAssignmentRepository;

import jakarta.ws.rs.NotFoundException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PolicyService {

    private static final String MFA_REQUIRED_ACTION = "CONFIGURE_TOTP";

    private final UserProfileAssignmentRepository assignmentRepository;
    private final SodRuleRepository sodRuleRepository;
    private final BusinessProfileRepository profileRepository;
    private final Keycloak keycloakClient;
    private final AuditService auditService;

    @Value("${keycloak.realm}")
    private String realmName;

    public PolicyService(
        UserProfileAssignmentRepository assignmentRepository,
        SodRuleRepository sodRuleRepository,
        BusinessProfileRepository profileRepository,
        @Qualifier("keycloakClient") Keycloak keycloakClient,
        AuditService auditService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.sodRuleRepository = sodRuleRepository;
        this.profileRepository = profileRepository;
        this.keycloakClient = keycloakClient;
        this.auditService = auditService;
    }

    // ═══════════════ SoD — vérification ═══════════════

    public SodCheckResult checkSoD(String userUuid, UUID requestedProfileId) {
        Set<UUID> activeProfileIds = assignmentRepository.findByUserUuidAndActiveTrue(userUuid)
            .stream()
            .map(UserProfileAssignment::getProfileId)
            .collect(Collectors.toSet());

        if (activeProfileIds.isEmpty()) {
            return new SodCheckResult("APPROVED", null);
        }

        List<SodRule> rules = sodRuleRepository.findAllInvolvingProfile(requestedProfileId);

        for (SodRule rule : rules) {
            UUID conflictingProfileId = rule.getProfileId().equals(requestedProfileId)
                ? rule.getIncompatibleProfileId()
                : rule.getProfileId();

            if (activeProfileIds.contains(conflictingProfileId)) {
                return new SodCheckResult("REJECTED", buildReason(rule, conflictingProfileId));
            }
        }

        return new SodCheckResult("APPROVED", null);
    }

    private String buildReason(SodRule rule, UUID conflictingProfileId) {
        if (rule.getReason() != null && !rule.getReason().isBlank()) {
            return rule.getReason();
        }
        String conflictingName = profileRepository.findById(conflictingProfileId)
            .map(BusinessProfile::getName)
            .orElse(conflictingProfileId.toString());
        return "Incompatible avec le profil déjà actif : " + conflictingName;
    }

    // ═══════════════ SoD — gestion des règles ═══════════════

    public List<SodRule> listRules(UUID profileId) {
        return profileId != null
            ? sodRuleRepository.findAllInvolvingProfile(profileId)
            : sodRuleRepository.findAll();
    }

    public SodRule createRule(CreateSodRuleRequest req) {
        if (req.profileId().equals(req.incompatibleProfileId())) {
            throw new IllegalArgumentException("Un profil ne peut pas être incompatible avec lui-même.");
        }
        BusinessProfile profile = profileRepository.findById(req.profileId())
            .orElseThrow(() -> new ResourceNotFoundException("Profil inexistant : " + req.profileId()));
        BusinessProfile incompatible = profileRepository.findById(req.incompatibleProfileId())
            .orElseThrow(() -> new ResourceNotFoundException("Profil inexistant : " + req.incompatibleProfileId()));

        SodRule rule = new SodRule();
        rule.setProfileId(profile.getId());
        rule.setIncompatibleProfileId(incompatible.getId());
        rule.setReason(req.reason());
        SodRule saved = sodRuleRepository.save(rule);

        auditService.log("CREATE_SOD_RULE", null,
            Map.of("profileId", profile.getId().toString(), "incompatibleProfileId", incompatible.getId().toString()));

        return saved;
    }

    public void deleteRule(UUID id) {
        SodRule rule = sodRuleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Règle SoD inexistante : " + id));
        sodRuleRepository.deleteById(id);

        auditService.log("DELETE_SOD_RULE", null,
            Map.of("profileId", rule.getProfileId().toString(), "incompatibleProfileId", rule.getIncompatibleProfileId().toString()));
    }

    // ═══════════════ MFA — recalcul ═══════════════

    public MfaRecomputeResult recomputeMfa(String userUuid) {
        List<UUID> activeProfileIds = assignmentRepository.findByUserUuidAndActiveTrue(userUuid)
            .stream()
            .map(UserProfileAssignment::getProfileId)
            .toList();

        boolean mfaRequired = !activeProfileIds.isEmpty()
            && profileRepository.findAllById(activeProfileIds).stream()
                .anyMatch(BusinessProfile::isMfaRequired);

        applyRequiredAction(userUuid, mfaRequired);

        auditService.log("RECOMPUTE_MFA", userUuid, Map.of("mfaRequired", mfaRequired));

        return new MfaRecomputeResult(mfaRequired);
    }

    private void applyRequiredAction(String userUuid, boolean mfaRequired) {
        UserResource userResource = keycloakClient.realm(realmName).users().get(userUuid);
        UserRepresentation user;
        try {
            user = userResource.toRepresentation();
        } catch (NotFoundException e) {
            throw new ResourceNotFoundException("Utilisateur Keycloak introuvable : " + userUuid);
        }

        List<String> actions = new ArrayList<>(
            user.getRequiredActions() != null ? user.getRequiredActions() : List.of()
        );
        boolean currentlyHasAction = actions.contains(MFA_REQUIRED_ACTION);

        // n'écrit dans Keycloak que si l'état doit réellement changer
        if (mfaRequired && !currentlyHasAction) {
            actions.add(MFA_REQUIRED_ACTION);
        } else if (!mfaRequired && currentlyHasAction) {
            actions.remove(MFA_REQUIRED_ACTION);
        } else {
            return; // déjà dans le bon état, rien à faire
        }

        user.setRequiredActions(actions);
        userResource.update(user);
    }

    // ═══════════════ ABAC — hors périmètre, stub ═══════════════

    public List<Object> listAbacRules() {
        return List.of(); // reporté — voir le document de planning
    }
}