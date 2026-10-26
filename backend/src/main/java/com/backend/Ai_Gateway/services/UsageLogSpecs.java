package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.UsageLog;
import com.backend.Ai_Gateway.enums.ProviderLocation;
import com.backend.Ai_Gateway.enums.RequestStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UsageLogSpecs {

    private UsageLogSpecs() {}

    public static Specification<UsageLog> filter(
            Long userId,
            Instant from,
            Instant to,
            String model,
            String provider,
            ProviderLocation location,
            String q
    ) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (userId != null) {
                preds.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (from != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            Join<?, ?> modelJoin = root.join("model", JoinType.LEFT);
            Join<?, ?> providerJoin = modelJoin.join("provider", JoinType.LEFT);

            if (model != null && !model.isBlank()) {
                String m = model.trim().toLowerCase(Locale.ROOT);
                preds.add(cb.or(
                        cb.equal(cb.lower(modelJoin.get("name")), m),
                        cb.equal(cb.lower(modelJoin.get("displayName")), m)
                ));
            }
            if (provider != null && !provider.isBlank()) {
                preds.add(cb.equal(cb.lower(providerJoin.get("name")), provider.trim().toLowerCase(Locale.ROOT)));
            }
            if (location != null) {
                preds.add(cb.equal(providerJoin.get("location"), location));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(modelJoin.get("name")), like),
                        cb.like(cb.lower(modelJoin.get("displayName")), like),
                        cb.like(cb.lower(providerJoin.get("name")), like)
                ));
            }

            if (query != null && UsageLog.class.equals(query.getResultType())) {
                query.distinct(true);
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }

    public static boolean isSuccess(RequestStatus status) {
        return status == RequestStatus.SUCCESS;
    }
}
