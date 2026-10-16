package com.eqima.keacloakadmin.controller;

import com.eqima.keacloakadmin.dto.AuditEventDto;
import com.eqima.keacloakadmin.dto.PagedResult;
import com.eqima.keacloakadmin.request.AuditEventRequest;
import com.eqima.keacloakadmin.service.AuditService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/audit/events")
@Validated
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    // Exposed for completeness / external callers, but as the spec notes,
    // internal services should call auditService.log(...) directly instead
    // of going through HTTP.
    // @PostMapping
    // @ResponseStatus(HttpStatus.CREATED)
    // public AuditEventDto create(@Valid @RequestBody AuditEventRequest req) {
    //     var saved = auditService.log(req.eventType(), req.actorUuid(), req.targetUserUuid(), req.details());
    //     return auditService.findById(saved.getId());
    // }

    @GetMapping
    public PagedResult<AuditEventDto> search(
        @RequestParam(required = false) String userUuid,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return auditService.search(userUuid, eventType, dateFrom, dateTo, page, size);
    }

    @GetMapping("/{id}")
    public AuditEventDto getById(@PathVariable UUID id) {
        return auditService.findById(id);
    }
}