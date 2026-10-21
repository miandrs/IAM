package com.eqima.keacloakadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SodCheckRequest(
    @NotBlank String userUuid,
    @NotNull UUID requestedProfileId
) {}
