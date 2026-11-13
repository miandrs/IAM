package com.eqima.keacloakadmin.models.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "business_profile")
@Getter @Setter
public class BusinessProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String organizationId; // optionnel — cf. note sur Organizations

    @Column(nullable = false)
    private boolean mfaRequired = false;

    @Column(name = "created_by") 
    private String createdBy;   // sub Keycloak du créateur
    @Column(name = "owner_role") 
    private String ownerRole;   // "ADMIN" ou "SUPERADMIN"
}