package com.eqima.keacloakadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RealmAdminDto {
    private String realm;
    private String clientId;
    private String username;
    private String firstname;
    private String lastname;
    private String email;
}
