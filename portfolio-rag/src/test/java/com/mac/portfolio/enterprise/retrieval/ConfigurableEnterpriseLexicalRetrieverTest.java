package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;
import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigurableEnterpriseLexicalRetrieverTest {

    @Test
    void bm25FailureFallsBackToPostgresFtsWhenFailOpen() {
        ParadeDbBm25LexicalRetriever bm25 = mock(ParadeDbBm25LexicalRetriever.class);
        PostgresFtsLexicalRetriever fts = mock(PostgresFtsLexicalRetriever.class);
        EnterpriseSearchHit hit = hit("fts-hit");
        when(bm25.search(anyString(), any(), anyInt()))
                .thenThrow(new EnterpriseBm25UnavailableException("BM25_TIMEOUT", "timeout"));
        when(fts.search(anyString(), any(), anyInt()))
                .thenReturn(EnterpriseLexicalSearchResult.of(List.of(hit), "POSTGRES_FTS"));

        ConfigurableEnterpriseLexicalRetriever retriever =
                new ConfigurableEnterpriseLexicalRetriever(bm25, fts, "PARADEDB_BM25", true);
        EnterpriseLexicalSearchResult result = retriever.search(
                "upload", EnterpriseAccessContext.from("engineering", "tenant-a"), 12);

        assertThat(result.backend()).isEqualTo("POSTGRES_FTS_FALLBACK");
        assertThat(result.fallbackReason()).isEqualTo("POSTGRES_FTS_FALLBACK:BM25_TIMEOUT");
        assertThat(result.hits()).extracting(EnterpriseSearchHit::chunkId).containsExactly("fts-hit");
    }

    @Test
    void bm25FailureIsVisibleWhenFailClosed() {
        ParadeDbBm25LexicalRetriever bm25 = mock(ParadeDbBm25LexicalRetriever.class);
        PostgresFtsLexicalRetriever fts = mock(PostgresFtsLexicalRetriever.class);
        EnterpriseBm25UnavailableException failure =
                new EnterpriseBm25UnavailableException("BM25_QUERY_FAILED", "database unavailable");
        when(bm25.search(anyString(), any(), anyInt())).thenThrow(failure);

        ConfigurableEnterpriseLexicalRetriever retriever =
                new ConfigurableEnterpriseLexicalRetriever(bm25, fts, "PARADEDB_BM25", false);

        assertThatThrownBy(() -> retriever.search("upload", EnterpriseAccessContext.from("admin", null), 12))
                .isSameAs(failure);
    }

    private EnterpriseSearchHit hit(String id) {
        return new EnterpriseSearchHit(id, "doc-" + id, id, "bench", "github", "Title", "content",
                "tenant-a", "engineering", "public", 0, 1.0, 1, Map.of());
    }
}
