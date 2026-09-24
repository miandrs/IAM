package com.eqima.keacloakadmin.dto;

public record ClientRequest(
    String realmName,
    String name,
    String uri,
    boolean enabled
) {

}
