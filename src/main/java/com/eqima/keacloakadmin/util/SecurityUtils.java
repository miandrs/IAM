package com.eqima.keacloakadmin.util;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component 
public class SecurityUtils {

    public SecurityUtils() {}

    public String currentUserId() {
        return jwt().getSubject();
    }

    public  String username() {
        return jwt().getClaimAsString("preferred_username");
    }

    public boolean isSuperAdmin() {
    return hasRealmRole("SUPERADMIN");
    }

    public boolean isAdmin() {
        return hasRealmRole("ADMIN");
    }

    public boolean hasRealmRole(String role) {
        return realmRoles().contains(role);
    }

    public List<String> realmRoles() {
        Map<String, Object> realmAccess = jwt().getClaim("realm_access");
        if (realmAccess == null) return List.of();
        Object roles = realmAccess.get("roles");
        return roles instanceof List<?> ? (List<String>) roles : List.of();
    }

    public List<String> groups() {
        List<String> groups = jwt().getClaimAsStringList("groups");
        return groups != null ? groups : List.of();
    }

    public List<String> organizations() {
        return groups().stream()
                .map(g -> g.startsWith("/") ? g.substring(1) : g)          // "/ACME/ADMINS" -> "ACME/ADMINS"
                .map(g -> g.contains("/") ? g.substring(0, g.indexOf('/')) : g)  // "ACME/ADMINS" -> "ACME"
                .distinct()
                .toList();
    }

    public boolean isInOrganization(String org) {
        return organizations().contains(org);
    }

    public Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Aucun utilisateur authentifié");
        }
        return jwt;
    }
}
