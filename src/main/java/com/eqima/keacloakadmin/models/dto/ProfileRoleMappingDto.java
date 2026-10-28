package com.eqima.keacloakadmin.models.dto;

import java.util.UUID;

public record ProfileRoleMappingDto(UUID id, UUID profileId, String clientUuid, String roleName) {}
