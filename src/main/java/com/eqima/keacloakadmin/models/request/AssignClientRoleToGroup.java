package com.eqima.keacloakadmin.models.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record AssignClientRoleToGroup(
    @NotBlank String groupName,
    List<ClientRoleRequest> clientRoles
) {

}
