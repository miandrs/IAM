package com.eqima.keacloakadmin.models.dto;

import java.util.UUID;

public record OrphanFindingDto(
    String type, String severity, String userUuid, String username,
    UUID referenceId, String detail, String suggestedAction
) {}
