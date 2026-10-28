package com.eqima.keacloakadmin.models.dto;

import java.util.UUID;

public record BusinessProfileDto(UUID id, String name, String organizationId, boolean mfaRequired) {}
