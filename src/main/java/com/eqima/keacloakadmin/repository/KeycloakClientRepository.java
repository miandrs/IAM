package com.eqima.keacloakadmin.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eqima.keacloakadmin.models.entity.KeycloakClient;
import java.util.List;


@Repository
public interface KeycloakClientRepository extends JpaRepository<KeycloakClient, UUID> {
    Optional<KeycloakClient> findByRealmName(String realmName);
    Optional<KeycloakClient> findByIssuerUri(String issuerUri);
}
