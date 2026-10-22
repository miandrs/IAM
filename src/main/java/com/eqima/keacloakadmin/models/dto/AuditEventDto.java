package com.eqima.keacloakadmin.models.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventDto(
    UUID id,
    String eventType,
    String actorUuid,
    String username,
    String targetUserUuid,
    Map<String, Object> details,
    Instant createdAt
) {}