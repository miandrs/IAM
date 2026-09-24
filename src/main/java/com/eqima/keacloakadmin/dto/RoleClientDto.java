package com.eqima.keacloakadmin.dto;

public record RoleClientDto(
    String clientUuid,
    String clientId,
    String roleUuid,
    String roleName,
    boolean enabled
) {

}
