package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.ContentLimits;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.provider.dto.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceFitToContextBudgetTest {

    @Test
    void emptyOrNull_returnsEmptyList() {
        assertTrue(ChatService.fitToContextBudget(null).isEmpty());
        assertTrue(ChatService.fitToContextBudget(List.of()).isEmpty());
    }

    @Test
    void keepsAllMessages_whenUnderBudget() {
        List<Message> messages = List.of(
                msg(MessageRole.USER, "hello"),
                msg(MessageRole.ASSISTANT, "hi"),
                msg(MessageRole.USER, "how are you?")
        );

        List<ChatMessage> fitted = ChatService.fitToContextBudget(messages);

        assertEquals(3, fitted.size());
        assertEquals("hello", fitted.get(0).content());
        assertEquals("how are you?", fitted.get(2).content());
    }

    @Test
    void dropsOldest_whenOverBudget() {
        int half = ContentLimits.CHAT_CONTEXT_CHAR_BUDGET / 2 + 10;
        String old = "O".repeat(half);
        String mid = "M".repeat(half);
        String recent = "R".repeat(100);

        List<Message> messages = List.of(
                msg(MessageRole.USER, old),
                msg(MessageRole.ASSISTANT, mid),
                msg(MessageRole.USER, recent)
        );

        List<ChatMessage> fitted = ChatService.fitToContextBudget(messages);

        assertEquals(2, fitted.size());
        assertEquals(mid, fitted.get(0).content());
        assertEquals(recent, fitted.get(1).content());
    }

    @Test
    void truncatesSingleOversizedLatestMessage() {
        String huge = "X".repeat(ContentLimits.CHAT_CONTEXT_CHAR_BUDGET + 5_000);
        List<Message> messages = List.of(msg(MessageRole.USER, huge));

        List<ChatMessage> fitted = ChatService.fitToContextBudget(messages);

        assertEquals(1, fitted.size());
        assertEquals(ContentLimits.CHAT_CONTEXT_CHAR_BUDGET, fitted.get(0).content().length());
    }

    @Test
    void preservesChronologicalOrder() {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(msg(MessageRole.USER, "m" + i));
        }

        List<ChatMessage> fitted = ChatService.fitToContextBudget(messages);

        assertEquals(List.of("m0", "m1", "m2", "m3", "m4"),
                fitted.stream().map(ChatMessage::content).toList());
    }

    private static Message msg(MessageRole role, String content) {
        Message m = new Message();
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
