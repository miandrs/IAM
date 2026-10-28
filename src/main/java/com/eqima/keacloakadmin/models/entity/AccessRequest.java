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
}
