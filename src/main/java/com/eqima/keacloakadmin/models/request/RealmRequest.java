package com.eqima.keacloakadmin.models.request;

public record RealmRequest(
    boolean enabled,
    String displayName
) {

}
