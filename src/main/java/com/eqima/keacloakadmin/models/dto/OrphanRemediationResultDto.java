package com.eqima.keacloakadmin.models.dto;

import java.util.UUID;

public record OrphanRemediationResultDto(String type, String userUuid, UUID referenceId, String action) {}
