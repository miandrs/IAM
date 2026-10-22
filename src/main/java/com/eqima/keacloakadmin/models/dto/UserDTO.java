package com.eqima.keacloakadmin.models.dto;

import java.time.Instant;
import java.util.List;

public record UserDTO(
    String id,
    String username,
    String firstname,
    String lastname,
    String email,
    Instant createdAt,
    boolean enabled,
    List<String> groups
) {
}
