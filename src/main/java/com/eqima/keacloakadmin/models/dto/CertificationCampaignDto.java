package com.eqima.keacloakadmin.models.dto;

import java.time.Instant;
import java.util.UUID;

public record CertificationCampaignDto(
    UUID id, String name, String status, UUID scopeOrganizationId, String createdBy,
    Instant createdAt, Instant dueAt, Instant closedAt,
    long total, long pending, long certified, long revoked, long expired
) {}
