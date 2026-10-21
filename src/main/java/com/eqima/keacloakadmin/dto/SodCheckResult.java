package com.eqima.keacloakadmin.dto;

public record SodCheckResult(
    String result,   // "APPROVED" | "REJECTED"
    String reason     // null si APPROVED
) {}
