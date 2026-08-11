package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;
import com.mac.portfolio.enterprise.model.EnterpriseRetrievalStrategy;
import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;
import com.mac.portfolio.enterprise.repository.EnterpriseDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EnterpriseRetrievalServiceTest {

    @Test
    void hybridRetrievalFusesVectorAndKeywordCandidates() {
        EnterpriseDocumentRepository repository = mock(EnterpriseDocumentRepository.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EnterpriseSearchHit vector = hit("vector", "vector result");
        EnterpriseSearchHit shared = hit("shared", "shared result");
        when(embeddingModel.embed("upload limits")).thenReturn(new float[]{0.1f, 0.2f});
        when(repository.searchVector(any(), any(), eq(12), eq(0.2))).thenReturn(List.of(shared, vector));
        when(repository.searchKeyword(anyString(), any(), eq(12))).thenReturn(List.of(shared));

        EnterpriseRetrievalService service = service(repository, embeddingModel, new NoOpReranker());
        EnterpriseRetrievalResult result = service.retrieve("upload limits",
                EnterpriseAccessContext.from("engineering", "default"), EnterpriseRetrievalStrategy.HYBRID);

        assertThat(result.hits()).extracting(EnterpriseSearchHit::chunkId)
                .containsExactly("shared", "vector");
        assertThat(result.metrics().candidateCount()).isEqualTo(2);
        verify(repository).searchVector(any(), any(), eq(12), eq(0.2));
        verify(repository).searchKeyword("upload limits", EnterpriseAccessContext.from("engineering", "default"), 12);
    }

    @Test
    void vectorFailureFallsBackToKeywordForHybridStrategy() {
        EnterpriseDocumentRepository repository = mock(EnterpriseDocumentRepository.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EnterpriseSearchHit keyword = hit("keyword", "keyword fallback");
        when(embeddingModel.embed(anyString())).thenThrow(new IllegalStateException("offline"));
        when(repository.searchKeyword(anyString(), any(), eq(12))).thenReturn(List.of(keyword));

        EnterpriseRetrievalResult result = service(repository, embeddingModel, new NoOpReranker())
                .retrieve("fallback", EnterpriseAccessContext.from("public", null), EnterpriseRetrievalStrategy.HYBRID);

        assertThat(result.hits()).singleElement().extracting(EnterpriseSearchHit::chunkId).isEqualTo("keyword");
        assertThat(result.metrics().candidateCount()).isEqualTo(1);
    }

    @Test
    void rerankerFailureKeepsRrfCandidates() {
        EnterpriseDocumentRepository repository = mock(EnterpriseDocumentRepository.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EnterpriseSearchHit hit = hit("shared", "rrf candidate");
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});
        when(repository.searchVector(any(), any(), eq(12), eq(0.2))).thenReturn(List.of(hit));
        when(repository.searchKeyword(anyString(), any(), eq(12))).thenReturn(List.of(hit));
        Reranker failing = mock(Reranker.class);
        when(failing.rerank(anyString(), anyList())).thenThrow(new IllegalStateException("reranker offline"));

        EnterpriseRetrievalResult result = service(repository, embeddingModel, failing)
                .retrieve("query", EnterpriseAccessContext.from("admin", null), EnterpriseRetrievalStrategy.HYBRID_RERANK);

        assertThat(result.hits()).singleElement().extracting(EnterpriseSearchHit::content).isEqualTo("rrf candidate");
    }

    @Test
    void rewrittenQueriesReuseTheExactSameAccessContext() {
        EnterpriseDocumentRepository repository = mock(EnterpriseDocumentRepository.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EnterpriseAccessContext access = EnterpriseAccessContext.from("finance", "tenant-a");
        when(repository.searchKeyword("original", access, 12)).thenReturn(List.of(hit("initial", "initial evidence")));
        when(repository.searchKeyword("exact billing term", access, 12)).thenReturn(List.of(hit("expanded", "billing evidence")));
        EnterpriseRetrievalService service = service(repository, embeddingModel, new NoOpReranker());

        EnterpriseRetrievalResult initial = service.retrieve(
                "original", access, EnterpriseRetrievalStrategy.KEYWORD);
        EnterpriseRetrievalResult expanded = service.expand(initial, "original", List.of("exact billing term"),
                access, EnterpriseRetrievalStrategy.KEYWORD);

        assertThat(expanded.hits()).extracting(EnterpriseSearchHit::chunkId)
                .containsExactlyInAnyOrder("initial", "expanded");
        assertThat(expanded.metrics().queryCount()).isEqualTo(2);
        verify(repository).searchKeyword("original", access, 12);
        verify(repository).searchKeyword("exact billing term", access, 12);
    }

    private EnterpriseRetrievalService service(EnterpriseDocumentRepository repository,
                                               EmbeddingModel embeddingModel,
                                               Reranker reranker) {
        return new EnterpriseRetrievalService(repository, embeddingModel, new RrfFusion(), reranker,
                12, 12, 5, 60, 9000, 0.2, true);
    }

    private EnterpriseSearchHit hit(String id, String content) {
        return new EnterpriseSearchHit(id, "doc-" + id, id, "bench", "github", "Title", content,
                "default", "engineering", "public", 0, 0.8, 1, Map.of());
    }
}
