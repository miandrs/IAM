package com.eqima.keacloakadmin.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eqima.keacloakadmin.models.entity.AccessRequest;
import com.eqima.keacloakadmin.models.entity.AccessRequestStatus;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {
    List<AccessRequest> findByStatus(AccessRequestStatus status);
}
