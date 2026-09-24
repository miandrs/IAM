package com.eqima.keacloakadmin.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
 
public record AssignClientRoleToUserRequest(
    @NotBlank String userUuid,
    List<ClientRoleRequest> clientRoles
) {
}
