package com.eqima.keacloakadmin.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eqima.keacloakadmin.entity.SodRule;

public interface SodRuleRepository extends JpaRepository<SodRule, UUID>{
    // les deux sens — une règle peut être posée dans n'importe quel ordre
    @Query("select r from SodRule r where r.profileId = :profileId or r.incompatibleProfileId = :profileId")
    List<SodRule> findAllInvolvingProfile(@Param("profileId") UUID profileId);
}
