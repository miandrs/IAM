package com.eqima.keacloakadmin.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Adresse IP du client pour la traçabilité.
 *
 * X-Forwarded-For est contrôlable par le client : on ne lui fait confiance que pour les N proxys inverses
 * connus devant l'application (app.client-ip.trusted-proxy-count, 0 par défaut = adresse TCP directe).
 * Derrière UN reverse proxy (nginx, Traefik…) qui AJOUTE l'IP du client à X-Forwarded-For, mettre 1.
 * Le client est alors l'entrée située N positions avant la fin de la liste ; les entrées plus à gauche
 * (éventuellement forgées) sont ignorées.
 */
@Component
public class ClientIpResolver {

    private static final int MAX_LEN = 45; // IPv6 textuelle max

    private final int trustedProxyCount;

    public ClientIpResolver(@Value("${app.client-ip.trusted-proxy-count:0}") int trustedProxyCount) {
        this.trustedProxyCount = Math.max(0, trustedProxyCount);
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) return null;
        String ip = request.getRemoteAddr();

        if (trustedProxyCount > 0) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] parts = xff.split(",");
                int idx = parts.length - trustedProxyCount;
                if (idx >= 0) {
                    String candidate = parts[idx].trim();
                    if (!candidate.isEmpty()) ip = candidate;
                }
            }
        }
        if (ip == null) return null;
        ip = ip.strip();
        return ip.length() > MAX_LEN ? ip.substring(0, MAX_LEN) : ip;
    }
}
