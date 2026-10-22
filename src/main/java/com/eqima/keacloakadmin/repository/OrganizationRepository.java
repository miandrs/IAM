package com.eqima.keacloakadmin.repository;

import com.eqima.keacloakadmin.models.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByKeycloakGroupId(String keycloakGroupId);
}
