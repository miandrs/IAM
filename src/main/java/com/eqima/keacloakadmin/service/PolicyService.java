package com.eqima.keacloakadmin.service;

import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.models.dto.MfaRecomputeResult;
import com.eqima.keacloakadmin.models.dto.SodCheckResult;
import com.eqima.keacloakadmin.models.dto.SodRuleDto;
import com.eqima.keacloakadmin.models.entity.BusinessProfile;
import com.eqima.keacloakadmin.models.entity.SodRule;
import com.eqima.keacloakadmin.models.entity.UserProfileAssignment;
import com.eqima.keacloakadmin.models.request.CreateSodRuleRequest;
import com.eqima.keacloakadmin.repository.BusinessProfileRepository;
import com.eqima.keacloakadmin.repository.SodRuleRepository;
import com.eqima.keacloakadmin.repository.UserProfileAssignmentRepository;
import com.eqima.keacloakadmin.util.SecurityUtils;

import jakarta.ws.rs.NotFoundException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PolicyService {

    private static final String MFA_REQUIRED_ACTION = "CONFIGURE_TOTP";
    private static final String OTP_CREDENTIAL_TYPE = "otp";

    private final UserProfileAssignmentRepository assignmentRepository;
    private final SodRuleRepository sodRuleRepository;
    private final BusinessProfileRepository profileRepository;
    private final Keycloak keycloakClient;
    private final AuditService auditService;
    private final SecurityUtils securityUtils; // CORRECTION

    @Value("${keycloak.realm}")
    private String realmName;

    public PolicyService(
        UserProfileAssignmentRepository assignmentRepository,
        SodRuleRepository sodRuleRepository,
        BusinessProfileRepository profileRepository,
        @Qualifier("keycloakClient") Keycloak keycloakClient,
        AuditService auditService,
        SecurityUtils securityUtils
    ) {
        this.assignmentRepository = assignmentRepository;
        this.sodRuleRepository = sodRuleRepository;
        this.profileRepository = profileRepository;
        this.keycloakClient = keycloakClient;
        this.auditService = auditService;
        this.securityUtils = securityUtils;
    }

    // ═══════════════ SoD — vérification ═══════════════

    public SodCheckResult checkSoD(String userUuid, UUID requestedProfileId) {
        Set<UUID> activeProfileIds = assignmentRepository.findByUserUuidAndActiveTrue(userUuid)
            .stream()
            .map(UserProfileAssignment::getProfileId)
            .collect(Collectors.toSet());

        // CORRECTION : refuse le doublon d'un profil déjà actif
        if (activeProfileIds.contains(requestedProfileId)) {
            return new SodCheckResult("REJECTED", "Profil déjà actif pour cet utilisateur.");
        }

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

    // CORRECTION : renvoie un DTO au lieu de l'entité
    public List<SodRuleDto> listRules(UUID profileId) {
        List<SodRule> rules = profileId != null
            ? sodRuleRepository.findAllInvolvingProfile(profileId)
            : sodRuleRepository.findAll();

        // One query for all the profiles involved, instead of 2 queries per rule
        Set<UUID> ids = new HashSet<>();
        for (SodRule r : rules) {
            ids.add(r.getProfileId());
            ids.add(r.getIncompatibleProfileId());
        }
        Map<UUID, String> names = profileRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(BusinessProfile::getId, BusinessProfile::getName));

        return rules.stream().map(r -> toRuleDto(r, names)).toList();
    }


    public SodRuleDto createRule(CreateSodRuleRequest req) {
        requireSuperAdmin(); // CORRECTION : les règles SoD sont globales

        if (req.profileId().equals(req.incompatibleProfileId())) {
            throw new IllegalArgumentException("Un profil ne peut pas être incompatible avec lui-même.");
        }
        BusinessProfile profile = profileRepository.findById(req.profileId())
            .orElseThrow(() -> new ResourceNotFoundException("Profil inexistant : " + req.profileId()));
        BusinessProfile incompatible = profileRepository.findById(req.incompatibleProfileId())
            .orElseThrow(() -> new ResourceNotFoundException("Profil inexistant : " + req.incompatibleProfileId()));

        // CORRECTION : (A,B) et (B,A) sont équivalentes
        if (sodRuleRepository.existsBetween(profile.getId(), incompatible.getId())) {
            throw new IllegalStateException("Une règle SoD existe déjà entre ces deux profils.");
        }

        SodRule rule = new SodRule();
        rule.setProfileId(profile.getId());
        rule.setIncompatibleProfileId(incompatible.getId());
        rule.setReason(req.reason());
        SodRule saved = sodRuleRepository.save(rule);

        auditService.log("CREATE_SOD_RULE", null,
            Map.of("profileId", profile.getId().toString(), "incompatibleProfileId", incompatible.getId().toString()));

        return new SodRuleDto(
            saved.getId(),
            profile.getId(), profile.getName(),
            incompatible.getId(), incompatible.getName(),
            saved.getReason()
        );
    }

    public void deleteRule(UUID id) {
        requireSuperAdmin(); // CORRECTION

        SodRule rule = sodRuleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Règle SoD inexistante : " + id));
        sodRuleRepository.deleteById(id);

        auditService.log("DELETE_SOD_RULE", null,
            Map.of("profileId", rule.getProfileId().toString(), "incompatibleProfileId", rule.getIncompatibleProfileId().toString()));
    }

    private void requireSuperAdmin() {
        if (!securityUtils.isSuperAdmin()) {
            throw new AccessDeniedException("Réservé au SUPERADMIN");
        }
    }

    private SodRuleDto toRuleDto(SodRule r, Map<UUID, String> names) {
        return new SodRuleDto(
            r.getId(),
            r.getProfileId(),
            names.getOrDefault(r.getProfileId(), r.getProfileId().toString()),
            r.getIncompatibleProfileId(),
            names.getOrDefault(r.getIncompatibleProfileId(), r.getIncompatibleProfileId().toString()),
            r.getReason()
        );
    }

    // ═══════════════ MFA — recalcul ═══════════════

    // Pas de contrôle de rôle ici : appelée en interne par GouvernanceService.provisionProfile
    // (donc par un ADMIN). Protégez l'éventuel endpoint HTTP au niveau du contrôleur.
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

        if (mfaRequired && !currentlyHasAction) {
            // CORRECTION : ne redemande pas un OTP déjà configuré
            boolean hasOtp = userResource.credentials().stream()
                .anyMatch(c -> OTP_CREDENTIAL_TYPE.equals(c.getType()));
            if (hasOtp) {
                return;
            }
            actions.add(MFA_REQUIRED_ACTION);
        } else if (!mfaRequired && currentlyHasAction) {
            actions.remove(MFA_REQUIRED_ACTION);
        } else {
            return; // déjà dans le bon état
        }

        user.setRequiredActions(actions);
        userResource.update(user);
    }

    // ═══════════════ ABAC — hors périmètre, stub ═══════════════

    public List<Object> listAbacRules() {
        return List.of(); // reporté — voir le document de planning
    }
}