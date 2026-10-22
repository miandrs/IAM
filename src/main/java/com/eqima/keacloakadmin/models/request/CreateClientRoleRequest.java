package com.eqima.keacloakadmin.models.request;

import jakarta.validation.constraints.NotBlank;

public record CreateClientRoleRequest(
    @NotBlank String clientId,
    @NotBlank String roleName
) {

}
