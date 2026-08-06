package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.MessageRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MessageFeedbackIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageRepository messageRepository;

    private String ownerSub;
    private String otherSub;
    private Long assistantMessageId;
    private Long conversationId;

    @BeforeEach
    void seed() {
        ownerSub = "fb-owner-" + UUID.randomUUID();
        otherSub = "fb-other-" + UUID.randomUUID();

        User owner = new User();
        owner.setKeycloakId(ownerSub);
        owner.setUsername("fbowner");
        owner.setEmail("fbowner@ai-gateway.local");
        owner.setEnabled(true);
        owner.setCreatedAt(Instant.now());
        owner = userRepository.save(owner);

        User other = new User();
        other.setKeycloakId(otherSub);
        other.setUsername("fbother");
        other.setEmail("fbother@ai-gateway.local");
        other.setEnabled(true);
        other.setCreatedAt(Instant.now());
        userRepository.save(other);

        Conversation conversation = new Conversation();
        conversation.setTitle("fb-test");
        conversation.setUser(owner);
        conversation.setCreatedAt(Instant.now());
        conversation.setUpdatedAt(Instant.now());
        conversation = conversationRepository.save(conversation);
        conversationId = conversation.getId();

        Message assistant = new Message();
        assistant.setRole(MessageRole.ASSISTANT);
        assistant.setContent("hello");
        assistant.setModelName("llama3.2:1b");
        assistant.setModelDisplayName("Llama");
        assistant.setCreatedAt(Instant.now());
        assistant.setConversation(conversation);
        assistantMessageId = messageRepository.save(assistant).getId();
    }

    @Test
    void ownerCanUpsertAndClearFeedback() throws Exception {
        mockMvc.perform(post("/api/messages/{id}/feedback", assistantMessageId)
                        .with(jwt().jwt(j -> j
                                .subject(ownerSub)
                                .claim("preferred_username", "fbowner")
                                .claim("email", "fbowner@ai-gateway.local"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"UP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myFeedback").value("UP"))
                .andExpect(jsonPath("$.modelName").value("llama3.2:1b"));

        mockMvc.perform(get("/api/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(j -> j.subject(ownerSub))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].myFeedback").value("UP"));

        mockMvc.perform(delete("/api/messages/{id}/feedback", assistantMessageId)
                        .with(jwt().jwt(j -> j.subject(ownerSub))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myFeedback").value(nullValue()));
    }

    @Test
    void nonOwnerGetsForbidden() throws Exception {
        mockMvc.perform(post("/api/messages/{id}/feedback", assistantMessageId)
                        .with(jwt().jwt(j -> j
                                .subject(otherSub)
                                .claim("preferred_username", "fbother")
                                .claim("email", "fbother@ai-gateway.local"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"DOWN\"}"))
                .andExpect(status().isForbidden());
    }

}
