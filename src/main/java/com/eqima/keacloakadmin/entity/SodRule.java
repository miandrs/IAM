package com.eqima.keacloakadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sod_rule", indexes = {
    @Index(name = "idx_sod_profile", columnList = "profileId"),
    @Index(name = "idx_sod_incompatible", columnList = "incompatibleProfileId")
})
@Getter @Setter
public class SodRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID profileId;

    @Column(nullable = false)
    private UUID incompatibleProfileId;

    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
