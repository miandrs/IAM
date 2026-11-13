package com.eqima.keacloakadmin.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eqima.keacloakadmin.models.dto.OrphanRemediationResultDto;
import com.eqima.keacloakadmin.models.dto.OrphanReportDto;
import com.eqima.keacloakadmin.models.request.OrphanRemediationRequest;
import com.eqima.keacloakadmin.service.OrphanAccountService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/governance/orphans")
@Validated
public class OrphanAccountController {

    private final OrphanAccountService orphanAccountService;

    public OrphanAccountController(OrphanAccountService orphanAccountService) {
        this.orphanAccountService = orphanAccountService;
    }

    // ADMIN : comptes de ses organisations. SUPERADMIN : tout, y compris les orphelins côté base.
    @GetMapping
    public OrphanReportDto scan() {
        return orphanAccountService.scanForCurrentUser();
    }

    // SUPERADMIN uniquement (vérifié dans le service). Désactive / clôture, ne supprime jamais.
    @PostMapping("/remediate")
    public OrphanRemediationResultDto remediate(@Valid @RequestBody OrphanRemediationRequest req) {
        return orphanAccountService.remediate(req);
    }
}
