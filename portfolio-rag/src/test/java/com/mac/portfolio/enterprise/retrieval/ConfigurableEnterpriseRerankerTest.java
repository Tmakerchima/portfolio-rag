package com.mac.portfolio.enterprise.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigurableEnterpriseRerankerTest {

    @Test
    void heuristicModeActuallyReordersCandidatesByQueryEvidence() {
        ConfigurableEnterpriseReranker reranker = new ConfigurableEnterpriseReranker(
                mock(ChatClient.class), new ObjectMapper(), ConfigurableEnterpriseReranker.Mode.HEURISTIC,
                10, 500);

        List<EnterpriseSearchHit> ranked = reranker.rerank("billing fallback reason", List.of(
                hit("first", "General deployment notes without the requested fields."),
                hit("second", "The billing fallback reason is emitted for cost reconciliation.")));

        assertThat(ranked).extracting(EnterpriseSearchHit::chunkId).containsExactly("second", "first");
        assertThat(ranked).extracting(EnterpriseSearchHit::rank).containsExactly(1, 2);
    }

    @Test
    void llmModeAcceptsOnlyKnownChunkIdsAndAppendsMissingCandidates() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("[\"second\",\"unknown\"]");
        ConfigurableEnterpriseReranker reranker = new ConfigurableEnterpriseReranker(
                client, new ObjectMapper(), ConfigurableEnterpriseReranker.Mode.LLM, 10, 500);

        List<EnterpriseSearchHit> ranked = reranker.rerank("billing fallback", List.of(
                hit("first", "general notes"), hit("second", "billing fallback evidence")));

        assertThat(ranked).extracting(EnterpriseSearchHit::chunkId).containsExactly("second", "first");
    }

    private EnterpriseSearchHit hit(String id, String content) {
        return new EnterpriseSearchHit(id, "doc-" + id, id, "bench", "github", "Title", content,
                "default", "engineering", "public", 0, 0.01, 1, Map.of());
    }
}
