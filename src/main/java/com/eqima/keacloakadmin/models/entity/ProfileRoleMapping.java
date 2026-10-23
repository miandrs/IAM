package com.eqima.keacloakadmin.models.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "profile_role_mapping", indexes = {
    @Index(name = "idx_prm_profile", columnList = "profileId")
})
@Getter @Setter
public class ProfileRoleMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID profileId;

    @Column(nullable = false)
    private String clientUuid; // UUID Keycloak du client, pas le clientId lisible — cf. RoleClientDto existant

    @Column(nullable = false)
    private String roleName;
}
