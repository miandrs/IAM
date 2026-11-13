package com.eqima.keacloakadmin.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eqima.keacloakadmin.models.entity.AccessCertificationCampaign;
import com.eqima.keacloakadmin.models.entity.CertificationStatus;

public interface AccessCertificationCampaignRepository extends JpaRepository<AccessCertificationCampaign, UUID> {
    List<AccessCertificationCampaign> findAllByOrderByCreatedAtDesc();
    List<AccessCertificationCampaign> findByStatus(CertificationStatus status);
    List<AccessCertificationCampaign> findByStatusAndDueAtBefore(CertificationStatus status, Instant instant);
}
