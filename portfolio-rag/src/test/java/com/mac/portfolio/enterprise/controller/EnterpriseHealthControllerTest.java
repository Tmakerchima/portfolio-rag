package com.mac.portfolio.enterprise.controller;

import com.mac.portfolio.enterprise.retrieval.EnterpriseLexicalHealth;
import com.mac.portfolio.enterprise.retrieval.EnterpriseLexicalRetriever;
import com.mac.portfolio.enterprise.service.EnterpriseCorpusService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnterpriseHealthControllerTest {

    @Test
    void bm25FailureReportsFtsFallbackWithoutSecrets() {
        EnterpriseCorpusService corpusService = mock(EnterpriseCorpusService.class);
        EnterpriseLexicalRetriever lexicalRetriever = mock(EnterpriseLexicalRetriever.class);
        when(corpusService.stats()).thenReturn(Map.of("status", "ACTIVE", "document_count", 3L, "chunk_count", 5L));
        when(lexicalRetriever.health()).thenReturn(new EnterpriseLexicalHealth(
                "PARADEDB_BM25", "POSTGRES_FTS_FALLBACK", false, "BM25_CAPABILITY_CHECK_FAILED"));

        Map<String, Object> body = new EnterpriseHealthController(corpusService, lexicalRetriever)
                .health().getBody();

        assertThat(body).isNotNull();
        assertThat(body).containsEntry("configuredLexicalBackend", "PARADEDB_BM25")
                .containsEntry("lexicalBackend", "POSTGRES_FTS_FALLBACK")
                .containsEntry("bm25", "DOWN")
                .containsEntry("lexicalReason", "BM25_CAPABILITY_CHECK_FAILED");
        assertThat(body).doesNotContainKeys("url", "username", "password");
    }
}
