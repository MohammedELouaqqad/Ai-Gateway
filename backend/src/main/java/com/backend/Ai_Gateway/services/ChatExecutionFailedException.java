package com.backend.Ai_Gateway.services;

/**
 * Sync chat execution exhausted all candidates (retry/fallback).
 * Carries the model id that failed last so callers can log ERROR usage correctly.
 */
public class ChatExecutionFailedException extends RuntimeException {

    private final Long failedModelId;

    public ChatExecutionFailedException(Long failedModelId, RuntimeException cause) {
        super(cause.getMessage(), cause);
        this.failedModelId = failedModelId;
    }

    public Long failedModelId() {
        return failedModelId;
    }

    public RuntimeException causeAsRuntime() {
        Throwable cause = getCause();
        return cause instanceof RuntimeException re ? re : this;
    }
}
