package com.eqima.keacloakadmin.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.exception.ConflictException;
import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.models.dto.DerivedRoleSodRuleDto;
import com.eqima.keacloakadmin.models.dto.RoleRefDto;
import com.eqima.keacloakadmin.models.dto.RoleSodRuleDto;
import com.eqima.keacloakadmin.models.dto.SodCheckResult;
import com.eqima.keacloakadmin.models.entity.BusinessProfile;
import com.eqima.keacloakadmin.models.entity.RoleSodRule;
import com.eqima.keacloakadmin.models.entity.SodRule;
import com.eqima.keacloakadmin.models.request.ClientRoleRequest;
import com.eqima.keacloakadmin.models.request.CreateRoleSodRuleRequest;
import com.eqima.keacloakadmin.repository.BusinessProfileRepository;
import com.eqima.keacloakadmin.repository.ProfileRoleMappingRepository;
import com.eqima.keacloakadmin.repository.RoleSodRuleRepository;
import com.eqima.keacloakadmin.repository.SodRuleRepository;
import com.eqima.keacloakadmin.util.SecurityUtils;

import jakarta.ws.rs.NotFoundException;

/**
 * SoD au niveau des rôles clients Keycloak : complète PolicyService (SoD au niveau des profils).
 *
 * Deux sources de conflits sont appliquées par checkRoles :
 *  1) les règles de rôles explicites (RoleSodRule) ;
 *  2) les règles de profils (SodRule) DÉRIVÉES à la volée : si A ⇄ B, un rôle propre à A est incompatible
 *     avec tout rôle propre à B (les rôles présents dans les deux profils sont exclus). Rien n'est stocké :
 *     la dérivation suit toujours l'état courant des règles de profils et des mappings profil→rôles.
 */
@Service
public class RoleSodService {

    private record RoleKey(String clientUuid, String roleName) {}
    private record Candidate(RoleKey partner, String reason) {}

    private final RoleSodRuleRepository ruleRepository;
    private final Keycloak keycloak;
    private final RoleClientService roleClientService;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;
    private final SodRuleRepository sodRuleRepository;
    private final ProfileRoleMappingRepository mappingRepository;
    private final BusinessProfileRepository profileRepository;
    private final ProfileVisibilityService visibility;

    @Value("${keycloak.realm}")
    private String realmName;

    public RoleSodService(RoleSodRuleRepository ruleRepository,
                          @Qualifier("keycloakClient") Keycloak keycloak,
                          RoleClientService roleClientService,
                          SecurityUtils securityUtils,
                          AuditService auditService,
                          SodRuleRepository sodRuleRepository,
                          ProfileRoleMappingRepository mappingRepository,
                          BusinessProfileRepository profileRepository,
                          ProfileVisibilityService visibility) {
        this.ruleRepository = ruleRepository;
        this.keycloak = keycloak;
        this.roleClientService = roleClientService;
        this.securityUtils = securityUtils;
        this.auditService = auditService;
        this.sodRuleRepository = sodRuleRepository;
        this.mappingRepository = mappingRepository;
        this.profileRepository = profileRepository;
        this.visibility = visibility;
    }

    // ═══════════════ Vérification ═══════════════

    /**
     * Vérifie qu'ajouter ces rôles à l'utilisateur ne crée aucun conflit. Ne modifie rien.
     * Les rôles que l'utilisateur possède déjà (directement, via un groupe ou un rôle composite) ne sont pas
     * considérés comme de nouvelles attributions. Un échec Keycloak se propage : en cas de doute on ne laisse pas passer.
     */
    public SodCheckResult checkRoles(String userUuid, List<ClientRoleRequest> requested) {
        if (requested == null || requested.isEmpty()) return new SodCheckResult("APPROVED", null);

        Set<RoleKey> requestedSet = requested.stream()
            .map(r -> new RoleKey(r.clientUuid(), r.roleName()))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        // 1) Conflits possibles par rôle demandé (règles de rôles + règles de profils dérivées),
        //    et clients Keycloak à interroger
        Map<UUID, Set<RoleKey>> profileRolesCache = new HashMap<>();
        Map<RoleKey, List<Candidate>> conflictsByRole = new LinkedHashMap<>();
        Set<String> clientsToLoad = new HashSet<>();
        for (RoleKey k : requestedSet) {
            List<Candidate> candidates = new ArrayList<>();
            for (RoleSodRule rule : ruleRepository.findAllInvolving(k.clientUuid(), k.roleName())) {
                RoleKey other = partner(rule, k);
                candidates.add(new Candidate(other, buildReason(rule, k, other)));
            }
            candidates.addAll(derivedCandidates(k, profileRolesCache));
            if (candidates.isEmpty()) continue;
            conflictsByRole.put(k, candidates);
            clientsToLoad.add(k.clientUuid());
            for (Candidate c : candidates) clientsToLoad.add(c.partner().clientUuid());
        }
        if (conflictsByRole.isEmpty()) return new SodCheckResult("APPROVED", null); // aucun appel Keycloak

        // 2) Rôles effectifs de l'utilisateur sur ces seuls clients
        UserResource user = keycloak.realm(realmName).users().get(userUuid);
        Set<RoleKey> held = new HashSet<>();
        for (String clientUuid : clientsToLoad) {
            try {
                for (RoleRepresentation role : user.roles().clientLevel(clientUuid).listEffective()) {
                    held.add(new RoleKey(clientUuid, role.getName()));
                }
            } catch (NotFoundException e) {
                // client supprimé : l'utilisateur ne peut plus en détenir de rôle
            }
        }

        // 3) Conflit = un rôle partenaire serait détenu après l'opération, et au moins un des deux est NOUVEAU
        Set<RoleKey> after = new HashSet<>(held);
        after.addAll(requestedSet);
        for (RoleKey k : requestedSet) {
            if (held.contains(k)) continue;
            for (Candidate c : conflictsByRole.getOrDefault(k, List.of())) {
                if (after.contains(c.partner())) {
                    return new SodCheckResult("REJECTED", c.reason());
                }
            }
        }
        return new SodCheckResult("APPROVED", null);
    }

    // ═══════════════ Règles de profils dérivées en conflits de rôles ═══════════════

    /**
     * Pour un rôle k : pour chaque profil P qui le contient et chaque règle P ⇄ Q, tous les rôles de Q sont
     * partenaires incompatibles de k. On exclut les rôles communs à P et Q (ils ne sont propres à aucun des deux),
     * et on ignore k s'il est lui-même dans Q (il n'est alors pas spécifique à P).
     */
    private List<Candidate> derivedCandidates(RoleKey k, Map<UUID, Set<RoleKey>> cache) {
        List<UUID> ownerProfiles = mappingRepository.findByClientUuidAndRoleName(k.clientUuid(), k.roleName())
            .stream().map(m -> m.getProfileId()).distinct().toList();
        if (ownerProfiles.isEmpty()) return List.of();

        List<Candidate> out = new ArrayList<>();
        for (UUID p : ownerProfiles) {
            for (SodRule rule : sodRuleRepository.findAllInvolvingProfile(p)) {
                UUID q = rule.getProfileId().equals(p) ? rule.getIncompatibleProfileId() : rule.getProfileId();
                Set<RoleKey> rolesP = rolesOf(p, cache);
                Set<RoleKey> rolesQ = rolesOf(q, cache);
                if (rolesQ.contains(k)) continue;
                String reason = null; // calculé une seule fois par paire, et seulement si nécessaire
                for (RoleKey partner : rolesQ) {
                    if (rolesP.contains(partner)) continue;
                    if (reason == null) reason = derivedReason(rule, p, q, k);
                    out.add(new Candidate(partner, reason));
                }
            }
        }
        return out;
    }

    private Set<RoleKey> rolesOf(UUID profileId, Map<UUID, Set<RoleKey>> cache) {
        return cache.computeIfAbsent(profileId, id -> mappingRepository.findByProfileId(id).stream()
            .map(m -> new RoleKey(m.getClientUuid(), m.getRoleName()))
            .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    // ne pas révéler un profil caché à l'appelant (même logique que PolicyService.buildReason)
    private String derivedReason(SodRule rule, UUID p, UUID q, RoleKey k) {
        Map<UUID, BusinessProfile> profiles = profileRepository.findAllById(List.of(p, q)).stream()
            .collect(Collectors.toMap(BusinessProfile::getId, x -> x));
        BusinessProfile bp = profiles.get(p);
        BusinessProfile bq = profiles.get(q);
        if (bp == null || bq == null || !visibility.canSee(bp) || !visibility.canSee(bq)) {
            return "Conflit de rôles : '" + k.roleName() + "' est incompatible avec un rôle déjà attribué à cet utilisateur.";
        }
        if (rule.getReason() != null && !rule.getReason().isBlank()) return rule.getReason();
        return "Conflit de rôles : '" + k.roleName() + "' (profil " + bp.getName() + ") est incompatible avec le profil "
            + bq.getName() + ", dont l'utilisateur détient déjà un rôle (attribué ou demandé en même temps).";
    }

    /**
     * Vue en lecture seule des règles de profils telles qu'elles s'appliquent aux rôles.
     * Même filtrage de visibilité que PolicyService.listRules ; les règles sans effet (aucun rôle propre
     * d'un des deux côtés) sont omises.
     */
    public List<DerivedRoleSodRuleDto> listDerivedRules() {
        List<SodRule> rules = sodRuleRepository.findAll();
        Set<UUID> ids = new HashSet<>();
        for (SodRule r : rules) { ids.add(r.getProfileId()); ids.add(r.getIncompatibleProfileId()); }
        Map<UUID, BusinessProfile> profiles = profileRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(BusinessProfile::getId, x -> x));
        var visible = visibility.visibilityFilter();
        Map<UUID, Set<RoleKey>> cache = new HashMap<>();

        List<DerivedRoleSodRuleDto> out = new ArrayList<>();
        for (SodRule r : rules) {
            BusinessProfile a = profiles.get(r.getProfileId());
            BusinessProfile b = profiles.get(r.getIncompatibleProfileId());
            if (a == null || b == null || !visible.test(a) || !visible.test(b)) continue;

            Set<RoleKey> rolesA = rolesOf(a.getId(), cache);
            Set<RoleKey> rolesB = rolesOf(b.getId(), cache);
            List<RoleRefDto> onlyA = rolesA.stream().filter(k -> !rolesB.contains(k))
                .map(k -> new RoleRefDto(k.clientUuid(), k.roleName())).toList();
            List<RoleRefDto> onlyB = rolesB.stream().filter(k -> !rolesA.contains(k))
                .map(k -> new RoleRefDto(k.clientUuid(), k.roleName())).toList();
            if (onlyA.isEmpty() || onlyB.isEmpty()) continue;

            out.add(new DerivedRoleSodRuleDto(r.getId(), a.getId(), a.getName(), onlyA,
                b.getId(), b.getName(), onlyB, r.getReason()));
        }
        return out;
    }

    public void assertRolesAllowed(String userUuid, List<ClientRoleRequest> requested) {
        SodCheckResult r = checkRoles(userUuid, requested);
        if (!"APPROVED".equals(r.result())) {
            throw new ConflictException(r.reason());
        }
    }

    private RoleKey partner(RoleSodRule rule, RoleKey self) {
        RoleKey a = new RoleKey(rule.getClientUuidA(), rule.getRoleNameA());
        return self.equals(a)
            ? new RoleKey(rule.getClientUuidB(), rule.getRoleNameB())
            : a;
    }

    private String buildReason(RoleSodRule rule, RoleKey requested, RoleKey other) {
        if (rule.getReason() != null && !rule.getReason().isBlank()) return rule.getReason();
        return "Conflit de rôles : '" + requested.roleName() + "' est incompatible avec '" + other.roleName()
            + "' (déjà attribué ou demandé en même temps).";
    }

    // ═══════════════ Gestion des règles ═══════════════

    public List<RoleSodRuleDto> listRules() {
        return ruleRepository.findAll().stream().map(this::toDto).toList();
    }

    public RoleSodRuleDto createRule(CreateRoleSodRuleRequest req) {
        requireAdmin();

        if (req.clientUuidA().equals(req.clientUuidB()) && req.roleNameA().equals(req.roleNameB())) {
            throw new IllegalArgumentException("Un rôle ne peut pas être incompatible avec lui-même.");
        }
        requireRoleExists(req.clientUuidA(), req.roleNameA());
        requireRoleExists(req.clientUuidB(), req.roleNameB());
        roleClientService.assertClientAssignable(req.clientUuidA());
        roleClientService.assertClientAssignable(req.clientUuidB());

        if (ruleRepository.existsBetween(req.clientUuidA(), req.roleNameA(), req.clientUuidB(), req.roleNameB())) {
            throw new IllegalStateException("Une règle SoD existe déjà entre ces deux rôles.");
        }

        RoleSodRule rule = new RoleSodRule();
        rule.setClientUuidA(req.clientUuidA());
        rule.setRoleNameA(req.roleNameA());
        rule.setClientUuidB(req.clientUuidB());
        rule.setRoleNameB(req.roleNameB());
        rule.setReason(req.reason());
        RoleSodRule saved = ruleRepository.save(rule);

        auditService.log("CREATE_ROLE_SOD_RULE", null, null,
            Map.of("ruleId", saved.getId().toString(), "roleA", req.roleNameA(), "roleB", req.roleNameB()));
        return toDto(saved);
    }

    public void deleteRule(UUID id) {
        requireSuperAdmin();
        RoleSodRule rule = ruleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Règle SoD de rôles inexistante : " + id));
        ruleRepository.delete(rule);
        auditService.log("DELETE_ROLE_SOD_RULE", null, null,
            Map.of("ruleId", id.toString(), "roleA", rule.getRoleNameA(), "roleB", rule.getRoleNameB()));
    }

    private void requireRoleExists(String clientUuid, String roleName) {
        try {
            keycloak.realm(realmName).clients().get(clientUuid).roles().get(roleName).toRepresentation();
        } catch (NotFoundException e) {
            throw new ResourceNotFoundException("Rôle introuvable : " + roleName + " (client " + clientUuid + ")");
        }
    }

    private RoleSodRuleDto toDto(RoleSodRule r) {
        return new RoleSodRuleDto(r.getId(), r.getClientUuidA(), r.getRoleNameA(),
            r.getClientUuidB(), r.getRoleNameB(), r.getReason());
    }

    private void requireAdmin() {
        if (!securityUtils.isAdmin()) throw new AccessDeniedException("Réservé au ADMIN");
    }

    private void requireSuperAdmin() {
        if (!securityUtils.isSuperAdmin()) throw new AccessDeniedException("Réservé au SUPERADMIN");
    }
}
