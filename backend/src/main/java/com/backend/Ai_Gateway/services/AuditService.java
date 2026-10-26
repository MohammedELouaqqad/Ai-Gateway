package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.AuditLog;
import com.backend.Ai_Gateway.enums.AuditAction;
import com.backend.Ai_Gateway.repositories.AuditLogRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Writes {@link AuditLog} entries for successful admin actions — called explicitly from each
 * admin controller right after its service call succeeds, never via AOP/interception.
 *
 * <p>{@link #record} is best-effort by design: a DB error writing the audit entry (or a
 * failure resolving the acting admin) must never turn an already-successful admin action into
 * an error response — but it must never be silent either, so every failure is logged at ERROR
 * and counted in {@code ai_gateway_audit_write_failures_total}, the same
 * "never block, never hide" pattern already used by
 * {@link com.backend.Ai_Gateway.services.guardrails.GuardMetricsRecorder} for provider-error
 * metrics.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;

    public AuditService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            MeterRegistry meterRegistry
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * @param targetId    nullable — e.g. no single target for a global guardrails config change
     * @param targetLabel nullable
     * @param details     nullable — structured diff, see {@link AuditDiffs}; must already be
     *                    free of secrets by the time it reaches here (this method does no
     *                    redaction of its own)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, String targetType, Long targetId, String targetLabel, Map<String, Object> details) {
        try {
            AuditActor actor = AuditActor.fromContext(userRepository);
            AuditLog entry = new AuditLog();
            entry.setActorUserId(actor.userId());
            entry.setActorLabel(actor.label());
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setTargetLabel(targetLabel);
            entry.setDetails(details);
            entry.setCreatedAt(Instant.now());
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Audit write failed: action={} targetType={} targetId={}", action, targetType, targetId, e);
            meterRegistry.counter("ai_gateway_audit_write_failures_total", "action", action.name()).increment();
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(AuditAction action, Instant from, Instant to, Pageable pageable) {
        return auditLogRepository.findAll(AuditLogSpecs.filter(action, from, to), pageable);
    }
}
