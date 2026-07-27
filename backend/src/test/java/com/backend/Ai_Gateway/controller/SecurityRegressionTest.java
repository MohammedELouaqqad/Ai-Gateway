package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityRegressionTest extends AbstractIntegrationTest {

    private static final String OWNER_SUB = "qa-owner-" + UUID.randomUUID();
    private static final String OTHER_SUB = "qa-other-" + UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Test
    void bug01_messagesIdorReturnsForbiddenForNonOwner() throws Exception {
        User owner = persistUser(OWNER_SUB, "owner-user");
        Conversation conversation = new Conversation();
        conversation.setTitle("Conv owner");
        conversation.setUser(owner);
        conversation.setCreatedAt(Instant.now());
        conversation.setUpdatedAt(Instant.now());
        conversation = conversationRepository.save(conversation);

        mockMvc.perform(get("/api/conversations/" + conversation.getId() + "/messages")
                        .with(jwt().jwt(j -> j
                                .subject(OTHER_SUB)
                                .claim("preferred_username", "other-user")
                                .claim("email", "other@ai-gateway.local"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("propriétaire")));

        mockMvc.perform(get("/api/conversations/" + conversation.getId() + "/messages")
                        .with(jwt().jwt(j -> j
                                .subject(OWNER_SUB)
                                .claim("preferred_username", "owner-user")
                                .claim("email", "owner@ai-gateway.local"))))
                .andExpect(status().isOk());
    }

    @Test
    void bug02_createProviderUsesDtoRejectsBlankAndDuplicateAndIgnoresId() throws Exception {
        String uniqueName = "Prov-QA-" + UUID.randomUUID();

        MvcResult first = mockMvc.perform(post("/api/providers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","type":"OLLAMA","baseUrl":"http://localhost:11434","location":"LOCAL","priority":9,"enabled":false}
                                """.formatted(uniqueName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(uniqueName))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        long firstId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(first.getResponse().getContentAsString())
                .get("id").asLong();

        String otherName = "Prov-QA-" + UUID.randomUUID();
        mockMvc.perform(post("/api/providers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":%d,"name":"%s","type":"OLLAMA","enabled":false}
                                """.formatted(firstId, otherName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(otherName))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(firstId)));

        // L'original n'a pas été écrasé
        mockMvc.perform(get("/api/providers").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].name".formatted(firstId)).value(uniqueName));

        mockMvc.perform(post("/api/providers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","type":"OLLAMA","enabled":false}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/providers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","type":"OLLAMA","enabled":false}
                                """.formatted(uniqueName)))
                .andExpect(status().isConflict());
    }

    @Test
    void bug03_adminRequiredForModelAndProviderMutations() throws Exception {
        mockMvc.perform(post("/api/providers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"NoAdmin","type":"OLLAMA","enabled":false}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/models")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"X","name":"x","providerId":1,"enabled":false,"isDefault":false}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/providers/1/status").with(jwt()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/models").with(jwt()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/providers").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void bug04_unreadableBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/providers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corps de requête invalide"));

        mockMvc.perform(post("/api/providers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"BadType-%s","type":"FOO","enabled":false}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corps de requête invalide"));
    }

    private User persistUser(String keycloakId, String username) {
        return userRepository.findByKeycloakId(keycloakId).orElseGet(() -> {
            User user = new User();
            user.setKeycloakId(keycloakId);
            user.setUsername(username);
            user.setEmail(username + "@ai-gateway.local");
            user.setEnabled(true);
            user.setCreatedAt(Instant.now());
            return userRepository.save(user);
        });
    }
}
