package com.eqima.keacloakadmin.request;

import jakarta.validation.constraints.NotBlank;

public record CreateClientRequest(
    @NotBlank String realmName,
    @NotBlank String clientId,
    @NotBlank String uri,
    boolean publicClient
){}
