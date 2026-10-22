package com.eqima.keacloakadmin.models.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "organization")
@Getter @Setter
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String keycloakGroupId; // pointe vers le groupe réel — name/path restent en Keycloak, jamais dupliqués ici

    @Column(nullable = false)
    private boolean active = true; // la seule chose que Keycloak n'a pas nativement pour un groupe
}
