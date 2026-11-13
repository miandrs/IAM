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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "access_certification_item",
    indexes = {
        @Index(name = "idx_aci_campaign", columnList = "campaignId"),
        @Index(name = "idx_aci_decision", columnList = "decision")
    },
    uniqueConstraints = @UniqueConstraint(name = "uk_aci_campaign_assignment", columnNames = {"campaignId", "assignmentId"}))
@Getter @Setter
public class AccessCertificationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID campaignId;

    @Column(nullable = false)
    private UUID assignmentId;

    @Column(nullable = false)
    private String userUuid;

    private String usernameSnapshot; // figé à l'ouverture : reste lisible si le compte disparaît ensuite

    @Column(nullable = false)
    private UUID profileId;

    private String profileNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificationDecision decision = CertificationDecision.PENDING;

    private String decidedBy;
    private Instant decidedAt;

    @Column(length = 1000)
    private String comment;
}
