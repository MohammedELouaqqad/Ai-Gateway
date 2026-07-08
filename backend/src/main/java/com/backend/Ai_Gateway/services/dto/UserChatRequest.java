package com.backend.Ai_Gateway.services.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserChatRequest {
    private Long conversationId;
    private String content;
    private String model;
    private boolean stream = false;
}
