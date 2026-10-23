package com.eqima.keacloakadmin.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eqima.keacloakadmin.models.entity.AccessRequestGate;

public interface AccessRequestGateRepository extends JpaRepository<AccessRequestGate, UUID> {
    List<AccessRequestGate> findByAccessRequestId(UUID accessRequestId);
}
