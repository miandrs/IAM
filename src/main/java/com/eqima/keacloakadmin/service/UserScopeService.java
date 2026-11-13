package com.eqima.keacloakadmin.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.models.entity.Organization;
import com.eqima.keacloakadmin.repository.OrganizationRepository;
import com.eqima.keacloakadmin.util.SecurityUtils;

import jakarta.ws.rs.NotFoundException;

/**
 * Périmètre d'un ADMIN : "ses" organisations et les utilisateurs qui y appartiennent. Tolérant (ne lève pas
 * sur un groupe sans Organization ni sur un utilisateur supprimé), contrairement à
 * GouvernanceService.resolveRequesterOrgs — nécessaire pour détecter justement les orphelins.
 */
@Service
public class UserScopeService {

    private final Keycloak keycloak;
    private final GroupService groupService;
    private final OrganizationRepository organizationRepository;
    private final SecurityUtils securityUtils;

    @Value("${keycloak.realm}")
    private String realmName;

    public UserScopeService(@Qualifier("keycloakClient") Keycloak keycloak,
                            GroupService groupService,
                            OrganizationRepository organizationRepository,
                            SecurityUtils securityUtils) {
        this.keycloak = keycloak;
        this.groupService = groupService;
        this.organizationRepository = organizationRepository;
        this.securityUtils = securityUtils;
    }

    /** IDs Organization de l'appelant (groupes de son token -> Organization). */
    // public Set<UUID> callerOrganizationIds() {
    //     List<String> groupIds = groupService.getAllGroups().stream().map(GroupRepresentation::getId).toList();
    //     if (groupIds.isEmpty()) return Set.of();
    //     return organizationRepository.findByKeycloakGroupIdIn(groupIds).stream()
    //         .map(Organization::getId).collect(Collectors.toSet());
    // }
    public Set<UUID> callerOrganizationIds() {
        return organizationIdsOfUser(securityUtils.currentUserId(), topLevelGroupIdsByName());
    }

    /** nom de groupe racine (MAJUSCULES) -> id du groupe Keycloak racine. */
    public Map<String, String> topLevelGroupIdsByName() {
        Map<String, String> m = new HashMap<>();
        for (GroupRepresentation g : groupService.listAllRaw()) {
            m.put(g.getName().toUpperCase(Locale.ROOT), g.getId());
        }
        return m;
    }

    /** Segment racine d'un chemin de groupe : "/ACME/ADMINS" -> "ACME". */
    public static String topSegment(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        int i = p.indexOf('/');
        return i >= 0 ? p.substring(0, i) : p;
    }

    /** Organizations d'un utilisateur ; vide si le compte n'existe plus ou n'a aucune organisation suivie. */
    public Set<UUID> organizationIdsOfUser(String userUuid, Map<String, String> topByName) {
        List<GroupRepresentation> groups;
        try {
            groups = keycloak.realm(realmName).users().get(userUuid).groups();
        } catch (NotFoundException e) {
            return Collections.emptySet();
        }
        Set<UUID> ids = new HashSet<>();
        for (GroupRepresentation g : groups) {
            String top = g.getPath() != null ? topSegment(g.getPath()) : g.getName();
            String groupId = topByName.get(top.toUpperCase(Locale.ROOT));
            if (groupId == null) continue;
            organizationRepository.findByKeycloakGroupId(groupId).ifPresent(o -> ids.add(o.getId()));
        }
        return ids;
    }

    public boolean callerCanManage(String userUuid) {
        if (securityUtils.isSuperAdmin() || securityUtils.isAdmin()) return true;
        Set<UUID> mine = callerOrganizationIds();
        if (mine.isEmpty()) return false;
        return !Collections.disjoint(mine, organizationIdsOfUser(userUuid, topLevelGroupIdsByName()));
    }

    /**
     * Garde-fou des endpoints qui agissent sur un compte par son id. SUPERADMIN : tout. ADMIN : uniquement les
     * membres de ses organisations, et jamais un SUPERADMIN. Autres rôles : refus.
     */
    public void assertCanManage(String userUuid) {
        if (securityUtils.isSuperAdmin()) return;
        if (!securityUtils.isAdmin()) throw new AccessDeniedException("Not allowed");
        assertNotSuperAdminTarget(userUuid);
        if (!callerCanManage(userUuid)) {
            throw new AccessDeniedException("Cet utilisateur ne relève pas de vos organisations.");
        }
    }

    /** Un ADMIN ne doit jamais pouvoir agir sur un compte SUPERADMIN (réinitialisation de mot de passe, etc.). */
    public void assertNotSuperAdminTarget(String userUuid) {
        if (securityUtils.isSuperAdmin()) return;
        try {
            boolean target = keycloak.realm(realmName).users().get(userUuid).roles().realmLevel().listEffective()
                .stream().anyMatch(r -> "SUPERADMIN".equals(r.getName()));
            if (target) throw new AccessDeniedException("Action interdite sur un compte SUPERADMIN.");
        } catch (NotFoundException e) {
            // compte inexistant : la suite renverra 404 / refus de périmètre
        }
    }

    /** Le groupe Keycloak doit faire partie des organisations de l'appelant (SUPERADMIN : tout). */
    public void assertGroupInScope(String keycloakGroupId) {
        if (securityUtils.isSuperAdmin()) return;
        if (!securityUtils.isAdmin()) throw new AccessDeniedException("Not allowed");
        boolean inScope = groupService.getAllGroups().stream().anyMatch(g -> g.getId().equals(keycloakGroupId));
        if (!inScope) throw new AccessDeniedException("Ce groupe ne relève pas de vos organisations.");
    }

    /** Idem pour une Organization. L'id peut être celui de l'Organization ou, par tolérance, celui du groupe Keycloak. */
    public void assertOrganizationInScope(UUID organizationId) {
        if (securityUtils.isSuperAdmin()) return;
        String groupId = organizationRepository.findById(organizationId)
            .map(Organization::getKeycloakGroupId)
            .orElse(organizationId.toString());
        assertGroupInScope(groupId);
    }

    public Optional<UserRepresentation> findUser(String userUuid) {
        try {
            return Optional.of(keycloak.realm(realmName).users().get(userUuid).toRepresentation());
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }

    public boolean userExists(String userUuid) {
        return findUser(userUuid).isPresent();
    }
}
