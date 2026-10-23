package com.eqima.keacloakadmin.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eqima.keacloakadmin.models.entity.ProfileRoleMapping;

public interface ProfileRoleMappingRepository extends JpaRepository<ProfileRoleMapping, UUID> {
    List<ProfileRoleMapping> findByProfileId(UUID profileId);
}
