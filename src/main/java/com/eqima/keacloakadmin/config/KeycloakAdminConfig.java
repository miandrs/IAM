package com.eqima.keacloakadmin.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminConfig {

    @Bean(name = "keycloakClient")
    public Keycloak keycloak(
        @Value("${keycloak.auth-server-url}") String serverUrl,
        @Value("${keycloak.realm}") String realm,
        @Value("${keycloak.resource}") String clientId,
        @Value("${keycloak.credentials.secret}") String clientSecret
    ) {
        return KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build();
    }

    @Bean(name = "keycloakAdmin")
    public Keycloak keycloakAdmin(
        @Value("${keycloak.auth-server-url}") String serverUrl,
        @Value("${keycloak.admin-username}") String adminUsername,
        @Value("${keycloak.admin-password}") String adminPassword,
        @Value("${keycloak.credentials.secret}") String clientSecret
    ) {
        return KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm("master")                // MUST be master
            .username(adminUsername)        // admin login
            .password(adminPassword)
            .clientId("admin-cli")          // built-in admin client
            .grantType(OAuth2Constants.PASSWORD)
            .clientSecret(clientSecret)
            .build();
    }
}
