package com.eqima.keacloakadmin.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateSodRuleRequest(
    @NotNull UUID profileId,
    @NotNull UUID incompatibleProfileId,
    String reason
) {}
