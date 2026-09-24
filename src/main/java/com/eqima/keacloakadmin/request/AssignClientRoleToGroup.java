package com.eqima.keacloakadmin.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record AssignClientRoleToGroup(
    @NotBlank String groupName,
    List<ClientRoleRequest> clientRoles
) {

}
