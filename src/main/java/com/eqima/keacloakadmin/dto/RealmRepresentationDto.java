package com.eqima.keacloakadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealmRepresentationDto {

    private String id;        // realm internal ID
    private String realm;     // realm name
    private boolean enabled;  // is the realm enabled
    private String displayName; // optional display name
}
