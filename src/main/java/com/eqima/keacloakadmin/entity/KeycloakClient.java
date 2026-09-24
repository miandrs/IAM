package com.eqima.keacloakadmin.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "keycloak_client")
public class KeycloakClient {
    
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private String issuerUri;
    private String realmName;

    private String clientId;
    private String clientSecret;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    // @OneToOne(mappedBy = "keycloakClient", fetch = FetchType.LAZY)
    // private KeycloakUser clientAdminUser;
}
