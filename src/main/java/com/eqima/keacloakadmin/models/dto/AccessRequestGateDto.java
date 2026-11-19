package com.eqima.keacloakadmin.models.dto;

import java.time.Instant;
import java.util.UUID;

public record AccessRequestGateDto(
    UUID organizationId, String organizationName, String decision, String approverUuid, String comment,
    Instant decidedAt, String approverIp
) {}
