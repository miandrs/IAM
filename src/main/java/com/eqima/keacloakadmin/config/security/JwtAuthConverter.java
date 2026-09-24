package com.eqima.keacloakadmin.config.security;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.of(
            jwtGrantedAuthoritiesConverter.convert(jwt).stream(), 
            extractRealmRoles(jwt).stream(),
            extractResourceRoles(jwt).stream()
        ).flatMap(s -> s)
        .collect(Collectors.toSet());
        return new JwtAuthenticationToken(jwt, authorities, (String) jwt.getClaim("preferred_username"));
    }   

    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Object realmAccessObj = jwt.getClaim("realm_access");
        if (!(realmAccessObj instanceof Map)) return Set.of();
        Map<?, ?> realmAccess = (Map<?, ?>) realmAccessObj;
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection)) return Set.of();
        Collection<?> rawRoles = (Collection<?>) rolesObj;
        return rawRoles.stream()
                .filter(Objects::nonNull)
                .map(role -> "ROLE_" + role.toString().toUpperCase())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    private Collection<GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Object resourceAccessObj = jwt.getClaim("resource_access");
        if (!(resourceAccessObj instanceof Map)) return Set.of();
        Map<?, ?> resourceAccess = (Map<?, ?>) resourceAccessObj;
        return resourceAccess.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Map)
                .flatMap(entry -> {
                    Map<?, ?> clientAccess = (Map<?, ?>) entry.getValue();
                    Object rolesObj = clientAccess.get("roles");
                    if (!(rolesObj instanceof Collection)) return Stream.empty();
                    Collection<?> rawRoles = (Collection<?>) rolesObj;
                    return rawRoles.stream()
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                })
                .collect(Collectors.toSet());
    }
}
