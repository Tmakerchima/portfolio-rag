package com.mac.portfolio.enterprise.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnterpriseQueryPlannerTest {

    @Test
    void disabledPlannerNeverCallsTheModel() {
        ChatClient client = mock(ChatClient.class);
        EnterpriseQueryPlanner planner = new EnterpriseQueryPlanner(
                client, new ObjectMapper(), false, 2, 3, 180, 500);

        assertThat(planner.plan("query", List.of())).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void insufficientFirstPassProducesBoundedIntentPreservingRewrites() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("""
                {"sufficient":false,"queries":["x-redwood-fallback-reason billing", "fallback telemetry envelope", "ignored"]}
                """);
        EnterpriseQueryPlanner planner = new EnterpriseQueryPlanner(
                client, new ObjectMapper(), true, 2, 3, 180, 500);

        assertThat(planner.plan("fallback header", List.of()))
                .containsExactly("x-redwood-fallback-reason billing", "fallback telemetry envelope");
    }
}
