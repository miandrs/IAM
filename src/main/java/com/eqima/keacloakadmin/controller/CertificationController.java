package com.eqima.keacloakadmin.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eqima.keacloakadmin.models.dto.CertificationCampaignDto;
import com.eqima.keacloakadmin.models.dto.CertificationItemDto;
import com.eqima.keacloakadmin.models.entity.CertificationDecision;
import com.eqima.keacloakadmin.models.request.CertificationDecisionRequest;
import com.eqima.keacloakadmin.models.request.CloseCertificationRequest;
import com.eqima.keacloakadmin.models.request.StartCertificationRequest;
import com.eqima.keacloakadmin.service.CertificationService;

import jakarta.validation.Valid;

// /governance/** est déjà réservé à ADMIN / SUPERADMIN par SecurityConfig ; le service re-vérifie et applique le périmètre.
@RestController
@RequestMapping("/governance/certifications")
@Validated
public class CertificationController {

    private final CertificationService certificationService;

    public CertificationController(CertificationService certificationService) {
        this.certificationService = certificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CertificationCampaignDto start(@Valid @RequestBody(required = false) StartCertificationRequest req) {
        return certificationService.start(req);
    }

    @GetMapping
    public List<CertificationCampaignDto> list() {
        return certificationService.listCampaigns();
    }

    @GetMapping("/pending")
    public List<CertificationItemDto> pending() {
        return certificationService.listPendingForCurrentReviewer();
    }

    @GetMapping("/{id}")
    public CertificationCampaignDto get(@PathVariable UUID id) {
        return certificationService.getCampaign(id);
    }

    @GetMapping("/{id}/items")
    public List<CertificationItemDto> items(@PathVariable UUID id,
                                            @RequestParam(required = false) CertificationDecision decision) {
        return certificationService.listItems(id, decision);
    }

    @PostMapping("/items/{itemId}/certify")
    public CertificationItemDto certify(@PathVariable UUID itemId,
                                        @RequestBody(required = false) CertificationDecisionRequest req) {
        return certificationService.certify(itemId, req != null ? req.comment() : null);
    }

    @PostMapping("/items/{itemId}/revoke")
    public CertificationItemDto revoke(@PathVariable UUID itemId,
                                       @RequestBody(required = false) CertificationDecisionRequest req) {
        return certificationService.revoke(itemId, req != null ? req.comment() : null);
    }

    @PostMapping("/{id}/close")
    public CertificationCampaignDto close(@PathVariable UUID id,
                                          @RequestBody(required = false) CloseCertificationRequest req) {
        boolean revoke = req != null && Boolean.TRUE.equals(req.revokeUnreviewed());
        return certificationService.close(id, revoke);
    }
}
