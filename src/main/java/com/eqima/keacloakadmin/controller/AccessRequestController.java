package com.eqima.keacloakadmin.controller;

import com.eqima.keacloakadmin.models.dto.AccessRequestDto;
import com.eqima.keacloakadmin.models.request.ApprovalRequest;
import com.eqima.keacloakadmin.models.request.CreateAccessRequestRequest;
import com.eqima.keacloakadmin.models.request.RejectionRequest;
import com.eqima.keacloakadmin.service.GouvernanceService;

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

    public AccessRequestController(GouvernanceService gouvernanceService) {
        this.gouvernanceService = gouvernanceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessRequestDto create(@Valid @RequestBody CreateAccessRequestRequest req) {
        return gouvernanceService.create(req);
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
    public AccessRequestDto approve(@PathVariable UUID id, @RequestBody(required = false) ApprovalRequest req) {
        String comment = req != null ? req.comment() : null;
        return gouvernanceService.approve(id, comment);
    }

    @PostMapping("/{id}/reject")
    public AccessRequestDto reject(@PathVariable UUID id, @Valid @RequestBody RejectionRequest req) {
        return gouvernanceService.reject(id, req.comment());
    }
}