package com.eqima.keacloakadmin.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.eqima.keacloakadmin.models.entity.AccessRequest;
import com.eqima.keacloakadmin.models.entity.AccessRequestStatus;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID>, JpaSpecificationExecutor<AccessRequest> {
    List<AccessRequest> findByStatus(AccessRequestStatus status);
    List<AccessRequest> findByUserUuidAndStatus(String userUuid, AccessRequestStatus status);
    boolean existsByProfileIdAndStatus(UUID profileId, AccessRequestStatus status);
}
