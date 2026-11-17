package com.eqima.keacloakadmin.models.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Deux rôles clients Keycloak qu'un même utilisateur ne doit pas cumuler.
 * Un rôle est identifié par (clientUuid, roleName), comme dans ProfileRoleMapping.
 * La règle est symétrique : (A,B) et (B,A) sont équivalentes.
 */
@Entity
@Table(name = "role_sod_rule", indexes = {
    @Index(name = "idx_rsod_a", columnList = "clientUuidA, roleNameA"),
    @Index(name = "idx_rsod_b", columnList = "clientUuidB, roleNameB")
})
@Getter @Setter
public class RoleSodRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String clientUuidA;
    @Column(nullable = false)
    private String roleNameA;

    @Column(nullable = false)
    private String clientUuidB;
    @Column(nullable = false)
    private String roleNameB;

    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
