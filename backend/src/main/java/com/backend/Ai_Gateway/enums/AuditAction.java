package com.backend.Ai_Gateway.enums;

/** What an {@code AuditLog} entry records — always a successful admin mutation, never an attempt. */
public enum AuditAction {
    USER_ENABLE,
    USER_DISABLE,
    PROVIDER_CREATE,
    PROVIDER_UPDATE,
    PROVIDER_DELETE,
    PROVIDER_ENABLE,
    PROVIDER_DISABLE,
    MODEL_CREATE,
    MODEL_UPDATE,
    MODEL_DELETE,
    MODEL_ENABLE,
    MODEL_DISABLE,
    MODEL_SET_DEFAULT,
    GUARDRAILS_UPDATE
}
