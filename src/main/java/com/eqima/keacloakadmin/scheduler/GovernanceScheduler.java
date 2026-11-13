package com.eqima.keacloakadmin.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.eqima.keacloakadmin.models.dto.OrphanReportDto;
import com.eqima.keacloakadmin.service.AuditService;
import com.eqima.keacloakadmin.service.CertificationService;
import com.eqima.keacloakadmin.service.OrphanAccountService;
import com.eqima.keacloakadmin.util.Details;

/**
 * Tâches périodiques de gouvernance. Aucune ne modifie d'accès : elles ouvrent / clôturent des campagnes et
 * journalisent un rapport d'orphelins (acteur "SYSTEM"). Les décisions restent humaines.
 * Si l'application tourne en plusieurs instances, activer ces tâches sur une seule (governance.scheduler.enabled=false
 * sur les autres) ou ajouter un verrou (ShedLock).
 */
@Component
public class GovernanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(GovernanceScheduler.class);

    private final CertificationService certificationService;
    private final OrphanAccountService orphanAccountService;
    private final AuditService auditService;

    @Value("${governance.scheduler.enabled:true}")
    private boolean enabled;

    public GovernanceScheduler(CertificationService certificationService,
                               OrphanAccountService orphanAccountService,
                               AuditService auditService) {
        this.certificationService = certificationService;
        this.orphanAccountService = orphanAccountService;
        this.auditService = auditService;
    }

    // Par défaut : le 1er de chaque trimestre à 06h00 (secondes minutes heures jour mois jour-semaine)
    @Scheduled(cron = "${governance.certification.cron:0 0 6 1 */3 *}")
    public void launchPeriodicCertification() {
        if (!enabled) return;
        try {
            certificationService.launchPeriodicCampaign()
                .ifPresent(c -> log.info("Campagne de certification périodique ouverte : {}", c.getId()));
        } catch (Exception e) {
            log.error("Échec du lancement de la certification périodique", e);
        }
    }

    // Tous les jours à 07h00 : clôture des campagnes échues (éléments restants -> EXPIRED)
    @Scheduled(cron = "${governance.certification.close-cron:0 0 7 * * *}")
    public void closeOverdueCertifications() {
        if (!enabled) return;
        try {
            int n = certificationService.closeOverdueCampaigns();
            if (n > 0) log.info("{} campagne(s) de certification échue(s) clôturée(s)", n);
        } catch (Exception e) {
            log.error("Échec de la clôture des campagnes échues", e);
        }
    }

    // Tous les lundis à 05h00
    @Scheduled(cron = "${governance.orphans.cron:0 0 5 * * MON}")
    public void scanOrphans() {
        if (!enabled) return;
        try {
            OrphanReportDto report = orphanAccountService.scanAsSystem();
            auditService.logSystem("ORPHAN_SCAN", null,
                Details.of("totalUsers", report.totalKeycloakUsers(), "findings", report.totalFindings(),
                    "byType", report.countsByType()));
            log.info("Scan des orphelins : {} constat(s) {}", report.totalFindings(), report.countsByType());
        } catch (Exception e) {
            log.error("Échec du scan des orphelins", e);
        }
    }
}
