package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.AuditLog;
import com.backend.Ai_Gateway.enums.AuditAction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AuditLogSpecs {

    private AuditLogSpecs() {}

    public static Specification<AuditLog> filter(AuditAction action, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (action != null) {
                preds.add(cb.equal(root.get("action"), action));
            }
            if (from != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }
}
