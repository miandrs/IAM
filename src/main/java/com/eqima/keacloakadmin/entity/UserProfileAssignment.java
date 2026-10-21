package com.eqima.keacloakadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

// Version minimale — suffisante pour que checkSoD fonctionne.
// La section Gouvernance (Access Requests) viendra écrire dans cette même table plus tard.
@Entity
@Table(name = "user_profile_assignment", indexes = {
    @Index(name = "idx_upa_user", columnList = "userUuid"),
    @Index(name = "idx_upa_profile", columnList = "profileId")
})
@Getter @Setter
public class UserProfileAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String userUuid;

    @Column(nullable = false)
    private UUID profileId;

    @Column(nullable = false)
    private boolean active = true;

    private Instant assignedAt;
    private Instant revokedAt;

    @PrePersist
    void onCreate() {
        if (assignedAt == null) assignedAt = Instant.now();
    }
}