package com.eqima.keacloakadmin.models.request;

import jakarta.validation.constraints.NotBlank;

public record CreateRoleSodRuleRequest(
    @NotBlank String clientUuidA,
    @NotBlank String roleNameA,
    @NotBlank String clientUuidB,
    @NotBlank String roleNameB,
    String reason
) {}
