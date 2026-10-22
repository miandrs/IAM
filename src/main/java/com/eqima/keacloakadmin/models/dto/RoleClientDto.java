package com.eqima.keacloakadmin.models.dto;

public record RoleClientDto(
    String clientUuid,
    String clientId,
    String roleUuid,
    String roleName,
    boolean enabled
) {

}
