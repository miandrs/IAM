package com.eqima.keacloakadmin.dto;

public record RealmRequest(
    boolean enabled,
    String displayName
) {

}
