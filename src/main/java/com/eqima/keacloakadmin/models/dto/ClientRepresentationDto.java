package com.eqima.keacloakadmin.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientRepresentationDto {
    private String id;         // internal Keycloak client ID
    private String clientId;   // client name/ID
    private String name;       // optional display name
    private boolean enabled;   // is the client enabled
    private boolean publicClient; // is it public
    private boolean bearerOnly;   // is it bearer-only client
}
