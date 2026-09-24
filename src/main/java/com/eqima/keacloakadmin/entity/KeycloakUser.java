package com.eqima.keacloakadmin.entity;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Entity
@Table(name="keycloak_user")
public class KeycloakUser {
    @Id
    private UUID id;
    private String username;
    private String firstname;
    private String lastname;
    private String email;
}
