package com.eqima.keacloakadmin.models.request;

public record CreateRoleMappingRequest(
    @jakarta.validation.constraints.NotBlank String clientUuid,
    @jakarta.validation.constraints.NotBlank String roleName
) {}
