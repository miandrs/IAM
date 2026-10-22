package com.eqima.keacloakadmin.models.dto;

public record ClientRequest(
    String realmName,
    String name,
    String uri,
    boolean enabled
) {

}
