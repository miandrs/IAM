package com.eqima.keacloakadmin.request;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
    @NotBlank String username,
    @Email String email,
    @NotBlank String firstname,
    @NotBlank String lastname,
    @NotBlank String password,
    boolean enabled,
    List<String> groups
) {

}
