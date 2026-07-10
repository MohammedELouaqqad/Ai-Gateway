package com.backend.Ai_Gateway.services.dto;

import com.backend.Ai_Gateway.config.ContentLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserChatRequest {
    private Long conversationId;

    @NotBlank
    @Size(max = ContentLimits.CHAT_MESSAGE_MAX)
    private String content;

    private String model;
    private boolean stream = false;
}
