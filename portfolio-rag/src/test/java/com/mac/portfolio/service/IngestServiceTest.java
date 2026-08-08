package com.mac.portfolio.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class IngestServiceTest {

    @Test
    void resumeIngestServiceIsNotAnApplicationRunner() {
        IngestService service = new IngestService(
                mock(VectorStore.class), mock(JdbcTemplate.class),
                mock(KnowledgeDocumentChunker.class), new KnowledgeChunkStore());

        assertThat(service).isNotInstanceOf(ApplicationRunner.class);
    }

    @Test
    void splitsEmbeddingRequestsIntoBatchesOfAtMostTenDocuments() {
        VectorStore vectorStore = mock(VectorStore.class);
        IngestService service = new IngestService(
                vectorStore,
                mock(JdbcTemplate.class),
                mock(KnowledgeDocumentChunker.class),
                new KnowledgeChunkStore());
        List<Document> chunks = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            chunks.add(new Document("chunk-" + i));
        }

        service.addInBatches(chunks);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> batches = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(3)).add(batches.capture());
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(10, 10, 1);
    }
}
