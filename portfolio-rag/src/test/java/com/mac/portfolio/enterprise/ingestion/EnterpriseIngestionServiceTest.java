package com.mac.portfolio.enterprise.ingestion;

import com.mac.portfolio.enterprise.model.EnterpriseChunk;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentRecord;
import com.mac.portfolio.enterprise.model.EnterpriseIndexedChunk;
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
        EnterpriseChunkContextualizer contextualizer = mock(EnterpriseChunkContextualizer.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EnterpriseDocumentInput input = input("same content");
        String hash = EnterpriseDocumentChunker.sha256(input.content());
        when(chunker.fingerprint()).thenReturn("structure-token-v2:max-700:overlap-80");
        when(contextualizer.fingerprint()).thenReturn("contextual-off-v1");
        String indexFingerprint = EnterpriseDocumentChunker.sha256(
                "structure-token-v2:max-700:overlap-80:contextual-off-v1:embedding:test-model:0");
        when(repository.findBySourceAndExternalId(input.source(), input.externalId()))
                .thenReturn(java.util.Optional.of(new EnterpriseDocumentRecord("doc-1", input.externalId(),
                        input.source(), hash, indexFingerprint, 4, false)));

        EnterpriseIngestionService service = new EnterpriseIngestionService(
                repository, chunker, contextualizer, embeddingModel, 5000);
        var result = service.ingest(input);

        assertThat(result.status()).isEqualTo(EnterpriseIngestionService.IngestionStatus.SKIPPED_UNCHANGED);
        verify(chunker, never()).chunk(any());
        verify(contextualizer, never()).contextualize(any(), any());
        verifyNoInteractions(embeddingModel);
        verify(repository, never()).upsertDocument(any(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void changedDocumentEmbedsAndReplacesChunksWithIncrementedVersion() {
        EnterpriseDocumentRepository repository = mock(EnterpriseDocumentRepository.class);
        EnterpriseDocumentChunker chunker = mock(EnterpriseDocumentChunker.class);
        EnterpriseChunkContextualizer contextualizer = mock(EnterpriseChunkContextualizer.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EnterpriseDocumentInput input = input("new content");
        when(chunker.fingerprint()).thenReturn("structure-token-v2:max-700:overlap-80");
        when(contextualizer.fingerprint()).thenReturn("contextual-off-v1");
        when(repository.findBySourceAndExternalId(input.source(), input.externalId()))
                .thenReturn(java.util.Optional.of(new EnterpriseDocumentRecord("doc-1", input.externalId(),
                        input.source(), "old-hash", 4, false)));
        EnterpriseChunk chunk = new EnterpriseChunk("chunk-1", 0, "new content", "Title > Section", 2);
        EnterpriseIndexedChunk indexedChunk = new EnterpriseIndexedChunk(
                chunk, "Document context", "Document context\n\nnew content", 4);
        when(chunker.chunk(any())).thenReturn(List.of(chunk));
        when(contextualizer.contextualize(any(), eq(chunk))).thenReturn(indexedChunk);
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));

        EnterpriseIngestionService service = new EnterpriseIngestionService(
                repository, chunker, contextualizer, embeddingModel, 5000);
        var result = service.ingest(input);

        assertThat(result.status()).isEqualTo(EnterpriseIngestionService.IngestionStatus.REINDEXED_CHANGED);
        assertThat(result.version()).isEqualTo(5);
        verify(repository).upsertDocument(eq(input), eq("doc-1"), eq(EnterpriseDocumentChunker.sha256("new content")),
                eq(EnterpriseDocumentChunker.sha256(
                        "structure-token-v2:max-700:overlap-80:contextual-off-v1:embedding:test-model:0")), eq(5));
        verify(repository).deleteChunks("doc-1");
        verify(embeddingModel).embed(List.of("Document context\n\nnew content"));
        verify(repository).insertChunk(eq("doc-1"), eq(indexedChunk), anyString(), anyMap(), any(float[].class));
    }

    @Test
    void embeddingFailureDoesNotMutateExistingDocument() {
        EnterpriseDocumentRepository repository = mock(EnterpriseDocumentRepository.class);
        EnterpriseDocumentChunker chunker = mock(EnterpriseDocumentChunker.class);
        EnterpriseChunkContextualizer contextualizer = mock(EnterpriseChunkContextualizer.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EnterpriseDocumentInput input = input("changed");
        when(chunker.fingerprint()).thenReturn("structure-token-v2:max-700:overlap-80");
        when(contextualizer.fingerprint()).thenReturn("contextual-off-v1");
        when(repository.findBySourceAndExternalId(input.source(), input.externalId()))
                .thenReturn(java.util.Optional.of(new EnterpriseDocumentRecord("doc-1", input.externalId(),
                        input.source(), "old-hash", 1, false)));
        EnterpriseChunk chunk = new EnterpriseChunk("chunk-1", 0, "changed");
        when(chunker.chunk(any())).thenReturn(List.of(chunk));
        when(contextualizer.contextualize(any(), eq(chunk))).thenReturn(
                new EnterpriseIndexedChunk(chunk, "", "changed", 1));
        when(embeddingModel.embed(anyList())).thenThrow(new IllegalStateException("embedding offline"));

        EnterpriseIngestionService service = new EnterpriseIngestionService(
                repository, chunker, contextualizer, embeddingModel, 5000);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.ingest(input))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).upsertDocument(any(), anyString(), anyString(), anyString(), anyInt());
        verify(repository, never()).deleteChunks(anyString());
    }

    @Test
    void changedIndexFingerprintReindexesUnchangedSourceText() {
        EnterpriseDocumentRepository repository = mock(EnterpriseDocumentRepository.class);
        EnterpriseDocumentChunker chunker = mock(EnterpriseDocumentChunker.class);
        EnterpriseChunkContextualizer contextualizer = mock(EnterpriseChunkContextualizer.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EnterpriseDocumentInput input = input("same source text");
        when(chunker.fingerprint()).thenReturn("structure-token-v2:max-700:overlap-80");
        when(contextualizer.fingerprint()).thenReturn("contextual-llm-v1:new-model");
        when(repository.findBySourceAndExternalId(input.source(), input.externalId()))
                .thenReturn(java.util.Optional.of(new EnterpriseDocumentRecord("doc-1", input.externalId(),
                        input.source(), EnterpriseDocumentChunker.sha256(input.content()), "legacy-v1", 2, false)));
        EnterpriseChunk chunk = new EnterpriseChunk("chunk-1", 0, input.content());
        EnterpriseIndexedChunk indexed = new EnterpriseIndexedChunk(
                chunk, "new context", "new context\n\n" + input.content(), 4);
        when(chunker.chunk(any())).thenReturn(List.of(chunk));
        when(contextualizer.contextualize(any(), eq(chunk))).thenReturn(indexed);
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[]{0.1f}));

        EnterpriseIngestionService service = new EnterpriseIngestionService(
                repository, chunker, contextualizer, embeddingModel, 5000);
        var result = service.ingest(input);

        assertThat(result.status()).isEqualTo(EnterpriseIngestionService.IngestionStatus.REINDEXED_PIPELINE_CHANGED);
        assertThat(result.version()).isEqualTo(3);
        verify(repository).deleteChunks("doc-1");
    }

    private EnterpriseDocumentInput input(String content) {
        return new EnterpriseDocumentInput("external-1", "bench", "github", "Title", content,
                "default", "engineering", "public", Map.of(), null);
    }
}
