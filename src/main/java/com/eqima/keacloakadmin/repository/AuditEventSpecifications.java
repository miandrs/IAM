package com.eqima.keacloakadmin.repository;

import com.eqima.keacloakadmin.models.entity.AuditEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public class AuditEventSpecifications {

    // Matches either the actor OR the target — "everything involving this user",
    // which is the useful question on an audit screen. Change to actor-only or
    // target-only if you'd rather split it into two params.
    public static Specification<AuditEvent> involvesUser(String userUuid) {
        return (root, query, cb) -> userUuid == null ? null :
            cb.or(cb.equal(root.get("actorUuid"), userUuid),
                  cb.equal(root.get("targetUserUuid"), userUuid));
    }

    public static Specification<AuditEvent> hasEventType(String eventType) {
        return (root, query, cb) -> eventType == null ? null :
            cb.equal(root.get("eventType"), eventType);
    }

    public static Specification<AuditEvent> createdFrom(Instant from) {
        return (root, query, cb) -> from == null ? null :
            cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<AuditEvent> createdTo(Instant to) {
        return (root, query, cb) -> to == null ? null :
            cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}