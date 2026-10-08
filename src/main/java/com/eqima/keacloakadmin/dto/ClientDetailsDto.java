package com.eqima.keacloakadmin.dto;

import java.util.List;

public record ClientDetailsDto(
    String id, String clientId, String name,
    Boolean enabled, Boolean publicClient, Boolean bearerOnly,
    String protocol, List<String> redirectUris, List<String> webOrigins) {}
