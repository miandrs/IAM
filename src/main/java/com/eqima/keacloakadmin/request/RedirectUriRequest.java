package com.eqima.keacloakadmin.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record RedirectUriRequest(@NotEmpty List<@NotBlank String> uris) {}
