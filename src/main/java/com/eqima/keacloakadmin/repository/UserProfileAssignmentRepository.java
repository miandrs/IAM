package com.eqima.keacloakadmin.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eqima.keacloakadmin.models.entity.UserProfileAssignment;
import java.util.UUID;
import java.util.List;


public interface UserProfileAssignmentRepository extends JpaRepository<UserProfileAssignment, UUID> {
    List<UserProfileAssignment> findByUserUuidAndActiveTrue(String userUuid);
}
