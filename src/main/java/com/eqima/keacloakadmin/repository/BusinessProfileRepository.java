package com.eqima.keacloakadmin.repository;

import com.eqima.keacloakadmin.models.entity.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {}