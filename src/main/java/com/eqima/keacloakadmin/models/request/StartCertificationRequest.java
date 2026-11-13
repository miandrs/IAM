package com.eqima.keacloakadmin.models.request;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StartCertificationRequest(
    String name,                         // optionnel : nom généré sinon
    @Min(1) @Max(365) Integer dueInDays, // optionnel : governance.certification.default-due-days sinon
    UUID organizationId                  // optionnel : limite la campagne à une organisation
) {}
