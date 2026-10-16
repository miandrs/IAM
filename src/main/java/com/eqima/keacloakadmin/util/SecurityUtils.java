package com.eqima.keacloakadmin.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class SecurityUtils {

    public SecurityUtils() {}

    public String currentUserId() {
        return jwt().getSubject();
    }

    public  String username() {
        return jwt().getClaimAsString("preferred_username");
    }

    public Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Aucun utilisateur authentifié");
        }
        return jwt;
    }
}
