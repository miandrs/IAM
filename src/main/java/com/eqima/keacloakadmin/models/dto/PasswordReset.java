package com.eqima.keacloakadmin.models.dto;

public record PasswordReset(
    String newPassword,
    boolean temporary
) {

}
