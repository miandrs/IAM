package com.eqima.keacloakadmin.models.request;

import jakarta.validation.constraints.NotBlank;

public record ClientRoleRequest(
    @NotBlank String clientUuid,
    @NotBlank String roleName
) {
}
