package com.eqima.keacloakadmin.service;

import com.eqima.keacloakadmin.exception.ResourceNotFoundException;
import com.eqima.keacloakadmin.models.dto.AuditEventDto;
import com.eqima.keacloakadmin.models.dto.PagedResult;
import com.eqima.keacloakadmin.models.entity.AuditEvent;
import com.eqima.keacloakadmin.models.entity.Organization;
import com.eqima.keacloakadmin.repository.AuditEventRepository;
import com.eqima.keacloakadmin.repository.OrganizationRepository;
import com.eqima.keacloakadmin.util.SecurityUtils;

import static com.eqima.keacloakadmin.repository.AuditEventSpecifications.*;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private final SecurityUtils securityUtils;
    private final AuditEventRepository repository;
    private final OrganizationRepository organizationRepository;
    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public AuditService(AuditEventRepository repository,
                        SecurityUtils securityUtils,
                        OrganizationRepository organizationRepository,
                        @Qualifier("keycloakClient") Keycloak keycloak) {
        this.repository = repository;
        this.securityUtils = securityUtils;
        this.organizationRepository = organizationRepository;
        this.keycloak = keycloak;
    }

    public AuditEvent log(String eventType, String targetUserUuid, Map<String, Object> details) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setActorUuid(securityUtils.currentUserId());   // bean injecté, plus de new SecurityUtils()
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

        if (securityUtils.isSuperAdmin()) {
            // voit tout
        } else if (securityUtils.isAdmin()) {
            spec = spec.and(visibleToCurrentAdmin());
        } else {
            throw new AccessDeniedException("Not allowed to read audit events");
        }

        Page<AuditEvent> result = repository.findAll(spec,
            PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return new PagedResult<>(
            result.getContent().stream().map(this::toDto).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    public AuditEventDto findById(UUID id) {
        AuditEvent event = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Événement d'audit introuvable : " + id));

        if (!securityUtils.isSuperAdmin()) {
            if (!securityUtils.isAdmin()) {
                throw new AccessDeniedException("Not allowed to read audit events");
            }
            // même règle de visibilité que pour la recherche
            boolean visible = repository.exists(
                Specification.<AuditEvent>where((r, q, cb) -> cb.equal(r.get("id"), id))
                    .and(visibleToCurrentAdmin()));
            if (!visible) {
                throw new AccessDeniedException("Événement hors de vos organisations");
            }
        }
        return toDto(event);
    }

    // ───────────── Visibilité ADMIN ─────────────

    private Specification<AuditEvent> visibleToCurrentAdmin() {
        String me = securityUtils.currentUserId();

        // noms de groupes du token -> groupes Keycloak (IDs)
        Set<String> orgNames = securityUtils.organizations().stream()
            .map(n -> n.toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());

        List<GroupRepresentation> myGroups = keycloak.realm(realm).groups().groups().stream()
            .filter(g -> orgNames.contains(g.getName().toUpperCase(Locale.ROOT)))
            .toList();

        Set<String> groupIds = myGroups.stream().map(GroupRepresentation::getId).collect(Collectors.toSet());

        Set<String> orgIds = groupIds.isEmpty() ? Set.of()
            : organizationRepository.findByKeycloakGroupIdIn(List.copyOf(groupIds)).stream()
                .map(Organization::getId).map(UUID::toString).collect(Collectors.toSet());

        Set<String> memberIds = new HashSet<>();
        for (String groupId : groupIds) {
            int first = 0;
            final int pageSize = 100;
            List<UserRepresentation> members;
            do {
                members = keycloak.realm(realm).groups().group(groupId).members(first, pageSize);
                members.forEach(m -> memberIds.add(m.getId()));
                first += pageSize;
            } while (members.size() == pageSize);
        }

        return (root, query, cb) -> {
            List<Predicate> ors = new ArrayList<>();

            ors.add(cb.equal(root.get("actorUuid"), me));

            if (!memberIds.isEmpty()) {
                ors.add(root.get("targetUserUuid").in(memberIds));
                ors.add(jsonText(cb, root.get("details"), "userId").in(memberIds));
            }
            if (!orgIds.isEmpty()) {
                ors.add(jsonText(cb, root.get("details"), "organizationId").in(orgIds));
            }
            if (!groupIds.isEmpty()) {
                ors.add(jsonText(cb, root.get("details"), "keycloakGroupId").in(groupIds));
                ors.add(jsonText(cb, root.get("details"), "groupId").in(groupIds));
            }
            return cb.or(ors.toArray(new Predicate[0]));
        };
    }

    private Expression<String> jsonText(CriteriaBuilder cb, Expression<?> details, String key) {
        Expression<String> extracted = cb.function(
            "json_extract", String.class, details, cb.literal("$." + key));
        return cb.function("json_unquote", String.class, extracted);
    }

    private AuditEventDto toDto(AuditEvent e) {
        return new AuditEventDto(e.getId(), e.getEventType(), e.getActorUuid(), e.getUsername(),
            e.getTargetUserUuid(), e.getDetails(), e.getCreatedAt());
    }
}