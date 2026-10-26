package com.backend.Ai_Gateway.provider.dto.openai;

import java.util.List;

public record OpenAIChatRequest(
        String model,
        List<OpenAIOutgoingMessage> messages
) {}
