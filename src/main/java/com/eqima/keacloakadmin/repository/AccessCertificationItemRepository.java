package com.eqima.keacloakadmin.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eqima.keacloakadmin.models.entity.AccessCertificationItem;
import com.eqima.keacloakadmin.models.entity.CertificationDecision;

public interface AccessCertificationItemRepository extends JpaRepository<AccessCertificationItem, UUID> {
    List<AccessCertificationItem> findByCampaignId(UUID campaignId);
    List<AccessCertificationItem> findByCampaignIdAndDecision(UUID campaignId, CertificationDecision decision);
    List<AccessCertificationItem> findByDecision(CertificationDecision decision);
    long countByCampaignIdAndDecision(UUID campaignId, CertificationDecision decision);
}
