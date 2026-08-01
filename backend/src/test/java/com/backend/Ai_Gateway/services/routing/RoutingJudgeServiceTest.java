package com.backend.Ai_Gateway.services.routing;

import com.backend.Ai_Gateway.config.RoutingProperties;
import com.backend.Ai_Gateway.entity.Message;
import com.backend.Ai_Gateway.entity.Model;
import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.enums.JudgeVerdict;
import com.backend.Ai_Gateway.enums.MessageRole;
import com.backend.Ai_Gateway.enums.ProviderType;
import com.backend.Ai_Gateway.provider.LLMProviderRouter;
import com.backend.Ai_Gateway.provider.dto.ChatRequest;
import com.backend.Ai_Gateway.provider.dto.ChatResponse;
import com.backend.Ai_Gateway.repositories.MessageRepository;
import com.backend.Ai_Gateway.repositories.ModelRepository;
import com.backend.Ai_Gateway.services.ModelPricingService;
import com.backend.Ai_Gateway.services.ModelService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingJudgeServiceTest {

    @Mock RoutingService routingService;
    @Mock RoutingDecisionLogRepository decisionLogRepository;
    @Mock MessageRepository messageRepository;
    @Mock ModelRepository modelRepository;
    @Mock ModelService modelService;
    @Mock ModelPricingService pricingService;
    @Mock LLMProviderRouter llmProviderRouter;

    RoutingJudgeService judgeService;
    RoutingProperties props;
    Model proposedModel;
    Provider openai;

    @BeforeEach
    void setUp() {
        props = new RoutingProperties();
        props.getJudge().setEnabled(true);
        props.getJudge().setBatchSize(5);
        props.getJudge().setLlmCompare(false);
        lenient().when(routingService.effectiveConfig()).thenReturn(props);

        openai = new Provider();
        openai.setType(ProviderType.OPENAI);
        proposedModel = new Model();
        proposedModel.setId(21L);
        proposedModel.setName("gpt-4o-mini");
        proposedModel.setProvider(openai);

        judgeService = new RoutingJudgeService(
                routingService,
                decisionLogRepository,
                messageRepository,
                modelRepository,
                modelService,
                pricingService,
                llmProviderRouter,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void heuristicCompare_prefersSubstantialNonRefusal() {
        assertThat(RoutingJudgeService.heuristicCompare("ok", "Here is a detailed answer with points:\n- a\n- b"))
                .isEqualTo(JudgeVerdict.PROPOSED_BETTER);
        assertThat(RoutingJudgeService.heuristicCompare("full answer here with detail", ""))
                .isEqualTo(JudgeVerdict.APPLIED_BETTER);
    }

    @Test
    void runBatch_runsProposedOnce_neverReCallsApplied() {
        RoutingDecisionLog log = readyLog();
        when(decisionLogRepository.findJudgeReadySample(eq("SHADOW"), any(Pageable.class)))
                .thenReturn(List.of(log));
        when(modelRepository.findByName("gpt-4o-mini")).thenReturn(Optional.of(proposedModel));
        when(llmProviderRouter.chat(eq(openai), any(ChatRequest.class)))
                .thenReturn(new ChatResponse("proposed answer with structure:\n- one\n- two", 10, 20));
        when(pricingService.calculateCostEur(10, 20, "gpt-4o-mini")).thenReturn(0.0012);
        when(decisionLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = judgeService.runBatch(1, "SHADOW");

        assertThat(result.get("judgedOk")).isEqualTo(1);
        verify(llmProviderRouter).chat(eq(openai), any(ChatRequest.class));
        // Exactly one LLM call (proposed only — never re-run applied)
        org.mockito.Mockito.verify(llmProviderRouter, org.mockito.Mockito.times(1)).chat(any(), any());
        assertThat(log.getProposedActualCostEur()).isEqualTo(0.0012);
        assertThat(log.getProposedResponseSnapshot()).contains("proposed answer");
        assertThat(log.getJudgeVerdict()).isNotBlank();
        assertThat(log.getJudgedAt()).isNotNull();
    }

    @Test
    void reconstructContext_usesHistoryUpToUserMessage() {
        RoutingDecisionLog log = readyLog();
        log.setConversationId(9L);
        log.setUserMessageId(2L);

        Message u1 = msg(1L, MessageRole.USER, "hi");
        Message a1 = msg(3L, MessageRole.ASSISTANT, "hello"); // id after userMessage — should not appear if we break at 2
        Message u2 = msg(2L, MessageRole.USER, "question");
        // Order by createdAsc: u1, u2, a1 — but ids 1,2,3. Break at userMessageId=2 → u1,u2
        when(messageRepository.findByConversation_IdOrderByCreatedAtAscIdAsc(9L))
                .thenReturn(List.of(u1, u2, a1));

        var messages = judgeService.reconstructContext(log);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).content()).isEqualTo("question");
    }

    private static RoutingDecisionLog readyLog() {
        RoutingDecisionLog log = new RoutingDecisionLog();
        log.setId(55L);
        log.setDiverged(true);
        log.setSelectedModelName("gpt-4o-mini");
        log.setAppliedModelName("llama3.2:1b");
        log.setPromptSnapshot("Summarize this.");
        log.setAppliedResponseSnapshot("short");
        log.setAppliedActualCostEur(0.0);
        return log;
    }

    private static Message msg(Long id, MessageRole role, String content) {
        Message m = new Message();
        m.setId(id);
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
