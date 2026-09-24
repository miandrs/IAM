package com.eqima.keacloakadmin.dto;

public record PasswordReset(
    String newPassword,
    boolean temporary
) {

}
