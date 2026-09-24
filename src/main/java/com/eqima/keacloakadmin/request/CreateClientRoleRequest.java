package com.eqima.keacloakadmin.request;

import jakarta.validation.constraints.NotBlank;

public record CreateClientRoleRequest(
    @NotBlank String clientId,
    @NotBlank String roleName
) {

}
