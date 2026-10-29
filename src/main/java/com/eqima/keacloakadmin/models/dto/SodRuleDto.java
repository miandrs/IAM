package com.eqima.keacloakadmin.models.dto;

import java.util.UUID;

public record SodRuleDto(UUID id, UUID profileId, String profileName, UUID incompatibleProfileId, String incompatibleProfileName, String reason) {}
