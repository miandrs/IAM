package com.eqima.keacloakadmin.models.dto;

import java.util.List;
import java.util.UUID;

/** Règle de profils (A ⇄ B) vue au niveau des rôles : rolesA = rôles propres à A, rolesB = rôles propres à B. */
public record DerivedRoleSodRuleDto(
    UUID sodRuleId,
    UUID profileId,
    String profileName,
    List<RoleRefDto> roles,
    UUID incompatibleProfileId,
    String incompatibleProfileName,
    List<RoleRefDto> incompatibleRoles,
    String reason
) {}
