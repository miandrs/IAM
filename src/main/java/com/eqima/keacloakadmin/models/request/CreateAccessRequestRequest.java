package com.eqima.keacloakadmin.models.request;

import java.util.UUID;

public record CreateAccessRequestRequest(
    @jakarta.validation.constraints.NotBlank String userUuid,
    @jakarta.validation.constraints.NotNull UUID profileId,
    String justification
) {}
