package com.eqima.keacloakadmin.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eqima.keacloakadmin.entity.KeycloakUser;

public interface KeycloakUserRepository extends JpaRepository<KeycloakUser, UUID> {
    // List<KeycloakUser> findByKeycloakClient(KeycloakClient keycloakClient);
}
