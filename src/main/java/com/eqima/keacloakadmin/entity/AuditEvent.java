package com.eqima.keacloakadmin.entity;

import com.eqima.keacloakadmin.util.JsonMapConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_event", indexes = {
    @Index(name = "idx_audit_actor", columnList = "actorUuid"),
    @Index(name = "idx_audit_target", columnList = "targetUserUuid"),
    @Index(name = "idx_audit_type", columnList = "eventType"),
    @Index(name = "idx_audit_created", columnList = "createdAt")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String actorUuid;

    private  String username;

    private String targetUserUuid; // nullable — not every event targets a user

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> details;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
