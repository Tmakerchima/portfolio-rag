package com.mac.portfolio.enterprise.ingestion;

import com.mac.portfolio.enterprise.model.EnterpriseChunk;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentRecord;
import com.mac.portfolio.enterprise.repository.EnterpriseDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class EnterpriseIngestionServiceTest {

    @Test
    void unchangedDocumentSkipsEmbeddingAndWrites() {
        EnterpriseDocumentRepository repository = mock(EnterpriseDocumentRepository.class);
        EnterpriseDocumentChunker chunker = mock(EnterpriseDocumentChunker.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EnterpriseDocumentInput input = input("same content");
        String hash = EnterpriseDocumentChunker.sha256(input.content());
        when(repository.findBySourceAndExternalId(input.source(), input.externalId()))
                .thenReturn(java.util.Optional.of(new EnterpriseDocumentRecord("doc-1", input.externalId(),
                        input.source(), hash, 4, false)));

        EnterpriseIngestionService service = new EnterpriseIngestionService(repository, chunker, embeddingModel, 5000);
        var result = service.ingest(input);

        assertThat(result.status()).isEqualTo(EnterpriseIngestionService.IngestionStatus.SKIPPED_UNCHANGED);
        verifyNoInteractions(chunker, embeddingModel);
        verify(repository, never()).upsertDocument(any(), anyString(), anyString(), anyInt());
    }

    @Test
    void changedDocumentEmbedsAndReplacesChunksWithIncrementedVersion() {
        EnterpriseDocumentRepository repository = mock(EnterpriseDocumentRepository.class);
        EnterpriseDocumentChunker chunker = mock(EnterpriseDocumentChunker.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EnterpriseDocumentInput input = input("new content");
        when(repository.findBySourceAndExternalId(input.source(), input.externalId()))
                .thenReturn(java.util.Optional.of(new EnterpriseDocumentRecord("doc-1", input.externalId(),
                        input.source(), "old-hash", 4, false)));
        EnterpriseChunk chunk = new EnterpriseChunk("chunk-1", 0, "new content");
        when(chunker.chunk(any())).thenReturn(List.of(chunk));
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));

        EnterpriseIngestionService service = new EnterpriseIngestionService(repository, chunker, embeddingModel, 5000);
        var result = service.ingest(input);

        assertThat(result.status()).isEqualTo(EnterpriseIngestionService.IngestionStatus.REINDEXED_CHANGED);
        assertThat(result.version()).isEqualTo(5);
        verify(repository).upsertDocument(eq(input), eq("doc-1"), eq(EnterpriseDocumentChunker.sha256("new content")), eq(5));
        verify(repository).deleteChunks("doc-1");
        verify(repository).insertChunk(eq("doc-1"), eq(chunk), anyString(), anyMap(), any(float[].class));
    }

    @Test
    void embeddingFailureDoesNotMutateExistingDocument() {
        EnterpriseDocumentRepository repository = mock(EnterpriseDocumentRepository.class);
        EnterpriseDocumentChunker chunker = mock(EnterpriseDocumentChunker.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EnterpriseDocumentInput input = input("changed");
        when(repository.findBySourceAndExternalId(input.source(), input.externalId()))
                .thenReturn(java.util.Optional.of(new EnterpriseDocumentRecord("doc-1", input.externalId(),
                        input.source(), "old-hash", 1, false)));
        when(chunker.chunk(any())).thenReturn(List.of(new EnterpriseChunk("chunk-1", 0, "changed")));
        when(embeddingModel.embed(anyList())).thenThrow(new IllegalStateException("embedding offline"));

        EnterpriseIngestionService service = new EnterpriseIngestionService(repository, chunker, embeddingModel, 5000);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.ingest(input))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).upsertDocument(any(), anyString(), anyString(), anyInt());
        verify(repository, never()).deleteChunks(anyString());
    }

    private EnterpriseDocumentInput input(String content) {
        return new EnterpriseDocumentInput("external-1", "bench", "github", "Title", content,
                "default", "engineering", "public", Map.of(), null);
    }
}
