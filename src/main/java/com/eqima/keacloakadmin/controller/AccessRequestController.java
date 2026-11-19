package com.eqima.keacloakadmin.controller;

import com.eqima.keacloakadmin.models.dto.AccessRequestDto;
import com.eqima.keacloakadmin.models.request.ApprovalRequest;
import com.eqima.keacloakadmin.models.request.CreateAccessRequestRequest;
import com.eqima.keacloakadmin.models.request.RejectionRequest;
import com.eqima.keacloakadmin.service.GouvernanceService;
import com.eqima.keacloakadmin.util.ClientIpResolver;

import jakarta.servlet.http.HttpServletRequest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/governance/access-requests")
@Validated
public class AccessRequestController {

    private final GouvernanceService gouvernanceService;
    private final ClientIpResolver clientIp;

    public AccessRequestController(GouvernanceService gouvernanceService, ClientIpResolver clientIp) {
        this.gouvernanceService = gouvernanceService;
        this.clientIp = clientIp;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessRequestDto create(@Valid @RequestBody CreateAccessRequestRequest req, HttpServletRequest http) {
        return gouvernanceService.create(req, clientIp.resolve(http));
    }

    @GetMapping
    public List<AccessRequestDto> listPending() {
        return gouvernanceService.listPendingForCurrentApprover();
    }

    @GetMapping("/{id}")
    public AccessRequestDto getById(@PathVariable UUID id) {
        return gouvernanceService.getById(id);
    }

    @PostMapping("/{id}/approve")
    public AccessRequestDto approve(@PathVariable UUID id, @RequestBody(required = false) ApprovalRequest req, HttpServletRequest http) {
        String comment = req != null ? req.comment() : null;
        return gouvernanceService.approve(id, comment, clientIp.resolve(http));
    }

    @PostMapping("/{id}/reject")
    public AccessRequestDto reject(@PathVariable UUID id, @Valid @RequestBody RejectionRequest req, HttpServletRequest http) {
        return gouvernanceService.reject(id, req.comment(), clientIp.resolve(http));
    }
}