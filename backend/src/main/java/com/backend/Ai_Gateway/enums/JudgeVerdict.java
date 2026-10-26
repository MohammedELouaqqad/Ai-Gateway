package com.backend.Ai_Gateway.enums;

/** Outcome of Phase 2 disagreement judging (stored answer vs one proposed-model run). */
public enum JudgeVerdict {
    PROPOSED_BETTER,
    APPLIED_BETTER,
    TIE,
    ERROR
}
