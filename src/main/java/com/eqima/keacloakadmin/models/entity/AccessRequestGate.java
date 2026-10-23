package com.eqima.keacloakadmin.models.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "access_request_gate", indexes = {
    @Index(name = "idx_gate_request", columnList = "accessRequestId")
})
@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
public class AccessRequestGate {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private UUID accessRequestId;
    @Column(nullable = false) private UUID organizationId; // un des groupes du demandeur, figé à la création

    @Column(nullable = false) private String decision; // PENDING | APPROVED | REJECTED
    private String approverUuid;
    private String comment;
    private Instant decidedAt;
}
