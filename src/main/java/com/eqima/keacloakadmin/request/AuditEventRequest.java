package com.eqima.keacloakadmin.request;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record AuditEventRequest(
    @NotBlank String eventType,
    @NotBlank String actorUuid,
    String targetUserUuid,
    Map<String, Object> details
) {}