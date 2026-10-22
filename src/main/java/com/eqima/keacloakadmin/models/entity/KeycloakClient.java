package com.eqima.keacloakadmin.models.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;
import org.hibernate.envers.Audited;

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
@Audited 
public class KeycloakClient extends Auditable {
    
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private String issuerUri;
    private String realmName;

    private String clientId;
    private String clientSecret;

    // @OneToOne(mappedBy = "keycloakClient", fetch = FetchType.LAZY)
    // private KeycloakUser clientAdminUser;
}
