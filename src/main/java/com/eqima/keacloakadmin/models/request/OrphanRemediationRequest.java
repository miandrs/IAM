package com.eqima.keacloakadmin.models.request;

import java.util.UUID;

import com.eqima.keacloakadmin.models.entity.OrphanType;

import jakarta.validation.constraints.NotNull;

public record OrphanRemediationRequest(
    @NotNull OrphanType type,
    String userUuid,   // requis pour NO_ORGANIZATION, ORGANIZATION_INACTIVE_OR_UNTRACKED, DISABLED_USER_ACTIVE_PROFILE
    UUID referenceId   // requis pour les types ASSIGNMENT_* et ACCESS_REQUEST_* (id de l'attribution / de la demande)
) {}
