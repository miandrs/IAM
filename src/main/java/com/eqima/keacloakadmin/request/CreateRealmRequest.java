package com.eqima.keacloakadmin.request;

import jakarta.validation.constraints.NotBlank;

public record CreateRealmRequest (
    @NotBlank String realm,
    CreateUserRequest userRequest
){}
