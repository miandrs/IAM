package com.eqima.keacloakadmin.models.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.eqima.keacloakadmin.util.JsonMapConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "access_request")
@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
public class AccessRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private String userUuid;
    @Column(nullable = false) private UUID profileId;
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccessRequestStatus status; // PENDING | PROVISIONED | REJECTED
    @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant decidedAt;

    // ── Traçabilité (figée à la création, jamais modifiée ensuite) ──
    @Column(updatable = false) private String requestedBy;          // sub Keycloak de qui a soumis la demande (≠ userUuid si pour un tiers)
    @Column(updatable = false) private String requestedByUsername;
    @Column(length = 45, updatable = false) private String requesterIp; // IP de l'auteur de la soumission

    // Origine au moment de la demande : organisations (tenants), produits, rôles, profil.
    // Instantané : les mappings du profil peuvent changer ensuite sans réécrire l'historique.
    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "TEXT", updatable = false)
    private Map<String, Object> origin;
}
