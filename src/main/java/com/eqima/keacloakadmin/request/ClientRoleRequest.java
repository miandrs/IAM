package com.eqima.keacloakadmin.request;

import jakarta.validation.constraints.NotBlank;

public record ClientRoleRequest(
    @NotBlank String clientUuid,
    @NotBlank String roleName
) {
}
