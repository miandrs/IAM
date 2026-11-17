package com.eqima.keacloakadmin.models.dto;

import java.util.UUID;

public record RoleSodRuleDto(
    UUID id,
    String clientUuidA,
    String roleNameA,
    String clientUuidB,
    String roleNameB,
    String reason
) {}
