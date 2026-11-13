package com.eqima.keacloakadmin.models.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OrphanReportDto(
    Instant generatedAt, int totalKeycloakUsers, int totalFindings,
    Map<String, Long> countsByType, List<OrphanFindingDto> findings
) {}
