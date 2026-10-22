package com.eqima.keacloakadmin.service;

import com.eqima.keacloakadmin.models.dto.AuditEventDto;
import com.eqima.keacloakadmin.models.dto.PagedResult;
import com.eqima.keacloakadmin.models.entity.AuditEvent;
import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.repository.AuditEventRepository;
import com.eqima.keacloakadmin.util.SecurityUtils;

import static com.eqima.keacloakadmin.repository.AuditEventSpecifications.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    // Call this directly from other services — no HTTP round-trip needed.
    // e.g. auditService.log("DELETE_CLIENT", currentUserUuid(), null, Map.of("clientId", clientId));
    public AuditEvent log(String eventType, String targetUserUuid, Map<String, Object> details) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);

        SecurityUtils securityUtils = new SecurityUtils();

        event.setActorUuid(securityUtils.currentUserId());
        event.setUsername(securityUtils.username());
        event.setTargetUserUuid(targetUserUuid);
        event.setDetails(details);
        return repository.save(event);
    }

    public PagedResult<AuditEventDto> search(String userUuid, String eventType, Instant from, Instant to, int page, int size) {
        Specification<AuditEvent> spec = Specification
            .where(involvesUser(userUuid))
            .and(hasEventType(eventType))
            .and(createdFrom(from))
            .and(createdTo(to));

        Page<AuditEvent> result = repository.findAll(spec,
            PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending()));

        return new PagedResult<>(
            result.getContent().stream().map(this::toDto).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    public AuditEventDto findById(UUID id) {
        return repository.findById(id)
            .map(this::toDto)
            .orElseThrow(() -> new ResourceNotFoundException("Événement d'audit introuvable : " + id));
    }

    private AuditEventDto toDto(AuditEvent e) {
        return new AuditEventDto(e.getId(), e.getEventType(), e.getActorUuid(), e.getUsername(),
            e.getTargetUserUuid(), e.getDetails(), e.getCreatedAt());
    }
}