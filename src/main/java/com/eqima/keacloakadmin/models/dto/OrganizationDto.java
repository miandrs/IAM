package com.eqima.keacloakadmin.models.dto;

import java.util.UUID;

public record OrganizationDto(UUID id, String keycloakGroupId, String name, boolean active) {}