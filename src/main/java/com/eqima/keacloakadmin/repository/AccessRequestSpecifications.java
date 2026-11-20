package com.eqima.keacloakadmin.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.eqima.keacloakadmin.models.entity.AccessRequest;
import com.eqima.keacloakadmin.models.entity.AccessRequestGate;
import com.eqima.keacloakadmin.models.entity.AccessRequestStatus;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class AccessRequestSpecifications {

    private AccessRequestSpecifications() {}

    public static Specification<AccessRequest> hasStatus(AccessRequestStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<AccessRequest> forUser(String userUuid) {
        return (root, query, cb) -> userUuid == null || userUuid.isBlank() ? null : cb.equal(root.get("userUuid"), userUuid);
    }

    public static Specification<AccessRequest> forProfile(UUID profileId) {
        return (root, query, cb) -> profileId == null ? null : cb.equal(root.get("profileId"), profileId);
    }

    public static Specification<AccessRequest> createdFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<AccessRequest> createdTo(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    /**
     * Demandes visibles par un appelant NON super-admin : celles qui le concernent (utilisateur visé) ou qu'il a
     * déposées, plus — pour un ADMIN — celles dont un des gates relève d'une de ses organisations.
     * Les gates sont figés à la création : une demande reste visible par l'organisation qui devait la traiter,
     * même si l'utilisateur a changé de groupe depuis.
     */
    public static Specification<AccessRequest> visibleTo(String me, Collection<UUID> organizationIds) {
        return (root, query, cb) -> {
            List<Predicate> ors = new ArrayList<>();
            ors.add(cb.equal(root.get("userUuid"), me));
            ors.add(cb.equal(root.get("requestedBy"), me));

            if (organizationIds != null && !organizationIds.isEmpty()) {
                Subquery<UUID> sub = query.subquery(UUID.class);
                Root<AccessRequestGate> gate = sub.from(AccessRequestGate.class);
                sub.select(gate.<UUID>get("accessRequestId"))
                   .where(cb.equal(gate.get("accessRequestId"), root.get("id")),
                          gate.get("organizationId").in(organizationIds));
                ors.add(cb.exists(sub));
            }
            return cb.or(ors.toArray(new Predicate[0]));
        };
    }
}
