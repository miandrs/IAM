package com.eqima.keacloakadmin.models.dto;

import java.time.Instant;
import java.util.UUID;

public record CertificationItemDto(
    UUID id, UUID campaignId, UUID assignmentId, String userUuid, String username,
    UUID profileId, String profileName, String decision, String decidedBy, Instant decidedAt, String comment
) {}
