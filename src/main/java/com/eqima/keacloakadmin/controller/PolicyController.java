package com.eqima.keacloakadmin.controller;

import com.eqima.keacloakadmin.models.dto.MfaRecomputeResult;
import com.eqima.keacloakadmin.models.dto.SodCheckResult;
import com.eqima.keacloakadmin.models.entity.SodRule;
import com.eqima.keacloakadmin.models.request.CreateSodRuleRequest;
import com.eqima.keacloakadmin.models.request.SodCheckRequest;
import com.eqima.keacloakadmin.service.PolicyService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/policy")
@Validated
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping("/sod/check")
    public SodCheckResult checkSoD(@Valid @RequestBody SodCheckRequest request) {
        return policyService.checkSoD(request.userUuid(), request.requestedProfileId());
    }

    @GetMapping("/sod-rules")
    public List<SodRule> listRules(@RequestParam(required = false) UUID profileId) {
        return policyService.listRules(profileId);
    }

    @PostMapping("/sod-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public SodRule createRule(@Valid @RequestBody CreateSodRuleRequest request) {
        return policyService.createRule(request);
    }

    @DeleteMapping("/sod-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable UUID id) {
        policyService.deleteRule(id);
    }

    @PostMapping("/mfa/recompute/{userUuid}")
    public MfaRecomputeResult recomputeMfa(@PathVariable String userUuid) {
        return policyService.recomputeMfa(userUuid);
    }

    @GetMapping("/abac-rules")
    public List<Object> listAbacRules() {
        return policyService.listAbacRules();
    }
}