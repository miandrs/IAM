package com.eqima.keacloakadmin.controller;

import com.eqima.keacloakadmin.models.dto.MfaRecomputeResult;
import com.eqima.keacloakadmin.models.dto.SodCheckResult;
import com.eqima.keacloakadmin.models.dto.SodRuleDto;
import com.eqima.keacloakadmin.models.entity.SodRule;
import com.eqima.keacloakadmin.models.request.CreateSodRuleRequest;
import com.eqima.keacloakadmin.models.request.SodCheckRequest;
import com.eqima.keacloakadmin.service.AssignmentGuardService;
import com.eqima.keacloakadmin.service.PolicyService;
import com.eqima.keacloakadmin.service.UserScopeService;
import com.eqima.keacloakadmin.util.SecurityUtils;

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
    private final UserScopeService userScope;
    private final AssignmentGuardService assignmentGuard;

    public PolicyController(PolicyService policyService, UserScopeService userScope,
                            AssignmentGuardService assignmentGuard) {
        this.policyService = policyService;
        this.userScope = userScope;
        this.assignmentGuard = assignmentGuard;
    }

    @PostMapping("/sod/check")
    public SodCheckResult checkSoD(@Valid @RequestBody SodCheckRequest request) {
        SecurityUtils securityUtils = new SecurityUtils();
        if (!securityUtils.currentUserId().equals(request.userUuid())) userScope.assertCanManage(request.userUuid());
        // même contrôle que lors d'une vraie attribution : SoD de profils PUIS SoD de rôles
        // (règles de rôles manuelles + règles de profils dérivées). Rejet → ConflictException (409).
        assignmentGuard.assertAllowed(request.userUuid(), request.requestedProfileId(), null);
        return new SodCheckResult("APPROVED", null);
    }

    @GetMapping("/sod-rules")
    public List<SodRuleDto> listRules(@RequestParam(required = false) UUID profileId) {
        return policyService.listRules(profileId);
    }

    @PostMapping("/sod-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public SodRuleDto createRule(@Valid @RequestBody CreateSodRuleRequest request) {
        return policyService.createRule(request);
    }

    @DeleteMapping("/sod-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable UUID id) {
        policyService.deleteRule(id);
    }

    @PostMapping("/mfa/recompute/{userUuid}")
    public MfaRecomputeResult recomputeMfa(@PathVariable String userUuid) {
        userScope.assertCanManage(userUuid);
        return policyService.recomputeMfa(userUuid);
    }

    @GetMapping("/abac-rules")
    public List<Object> listAbacRules() {
        return policyService.listAbacRules();
    }
}