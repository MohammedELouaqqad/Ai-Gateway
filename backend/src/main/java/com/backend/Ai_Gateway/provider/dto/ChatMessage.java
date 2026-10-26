package com.backend.Ai_Gateway.provider.dto;

import com.backend.Ai_Gateway.enums.MessageRole;

import java.util.List;

public record ChatMessage (
    MessageRole role,
    String content,
    /** Empty for every text-only message (the overwhelming majority) — only the current
     *  turn's user message carries an entry when the request came with an attachment (see
     *  {@code ChatService#prepareChat}). History reconstruction ({@code fitToContextBudget})
     *  deliberately does NOT re-embed past images on every reload (cost/latency), so this is
     *  populated for at most one message per request today. */
    List<ChatAttachment> attachments
) {
    /** Canonical text-only constructor — every pre-existing call site keeps compiling
     *  unchanged, and produces the exact same behavior as before this field existed. */
    public ChatMessage(MessageRole role, String content) {
        this(role, content, List.of());
    }
}
