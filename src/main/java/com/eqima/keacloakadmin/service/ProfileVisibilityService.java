package com.eqima.keacloakadmin.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.models.entity.BusinessProfile;
import com.eqima.keacloakadmin.util.SecurityUtils;

@Service
public class ProfileVisibilityService {

    public static final String OWNER_ADMIN = "ADMIN";
    public static final String OWNER_SUPERADMIN = "SUPERADMIN";

    private final SecurityUtils security;
    private final UserScopeService userScope;

    public ProfileVisibilityService(SecurityUtils security, UserScopeService userScope) {
        this.security = security;
        this.userScope = userScope;
    }

    /** Prépare le contexte une seule fois (appelant + groupes), à réutiliser pour tout un lot de profils. */
    public Predicate<BusinessProfile> visibilityFilter() {
        if (security.isSuperAdmin()) return p -> true;

        String me = security.currentUserId();
        Set<UUID> myOrgs = userScope.callerOrganizationIds();
        Set<String> myOrgIds = myOrgs.stream().map(UUID::toString).collect(Collectors.toSet());
        Map<String, String> top = userScope.topLevelGroupIdsByName();
        Map<String, Set<UUID>> orgsByCreator = new HashMap<>();

        return p -> {
            if (!OWNER_ADMIN.equals(p.getOwnerRole())) return false;           // SUPERADMIN ou ancien profil : jamais
            if (me.equals(p.getCreatedBy())) return true;                      // son propre profil
            if (p.getOrganizationId() != null && myOrgIds.contains(p.getOrganizationId())) return true;  // profil de mon organisation
            if (p.getCreatedBy() == null || myOrgs.isEmpty()) return false;
            Set<UUID> theirs = orgsByCreator.computeIfAbsent(
                p.getCreatedBy(), id -> userScope.organizationIdsOfUser(id, top));
            return !Collections.disjoint(myOrgs, theirs);                      // même organisation que le créateur
        };
    }

    public boolean canSee(BusinessProfile p) {
        return visibilityFilter().test(p);
    }

    /** 404 et non 403 : on ne révèle pas l'existence d'un profil caché. */
    public BusinessProfile requireVisible(BusinessProfile p) {
        if (!canSee(p)) throw new ResourceNotFoundException("Profil introuvable : " + p.getId());
        return p;
    }
}
