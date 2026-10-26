package com.backend.Ai_Gateway.enums;

/** How a feedback signal was produced. */
public enum FeedbackSource {
    /** User clicked 👍/👎 */
    EXPLICIT,
    /** User regenerated the assistant reply (implicit negative). */
    REGENERATED
    // EDITED: add only when an edit-assistant feature ships (no empty reserved flags).
}
