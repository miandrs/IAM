package com.eqima.keacloakadmin.models.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "access_certification_campaign", indexes = {
    @Index(name = "idx_acc_status", columnList = "status")
})
@Getter @Setter
public class AccessCertificationCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificationStatus status = CertificationStatus.OPEN;

    private UUID scopeOrganizationId; // null = toutes les organisations visibles par le lanceur

    @Column(nullable = false)
    private String createdBy; // UUID Keycloak du lanceur, ou "SYSTEM" pour la campagne planifiée

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant dueAt;
    private Instant closedAt;
    private String closedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
