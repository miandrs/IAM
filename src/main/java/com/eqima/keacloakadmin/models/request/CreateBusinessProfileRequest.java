package com.eqima.keacloakadmin.models.request;

public record CreateBusinessProfileRequest(
    @jakarta.validation.constraints.NotBlank String name,
    String organizationId,
    boolean mfaRequired
) {}
