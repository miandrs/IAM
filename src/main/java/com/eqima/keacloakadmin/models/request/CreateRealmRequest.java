package com.eqima.keacloakadmin.models.request;

import jakarta.validation.constraints.NotBlank;

public record CreateRealmRequest (
    @NotBlank String realm,
    CreateUserRequest userRequest
){}
