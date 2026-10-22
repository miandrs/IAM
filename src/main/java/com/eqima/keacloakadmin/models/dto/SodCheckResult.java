package com.eqima.keacloakadmin.models.dto;

public record SodCheckResult(
    String result,   // "APPROVED" | "REJECTED"
    String reason     // null si APPROVED
) {}
