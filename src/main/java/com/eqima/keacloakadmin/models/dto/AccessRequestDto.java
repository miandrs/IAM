package com.eqima.keacloakadmin.models.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccessRequestDto(
    UUID id, String userUuid, UUID profileId, String justification,
    String status, Instant createdAt, Instant decidedAt,
    List<AccessRequestGateDto> gates
) {}
