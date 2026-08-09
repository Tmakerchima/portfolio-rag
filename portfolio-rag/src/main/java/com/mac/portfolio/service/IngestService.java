package com.mac.portfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Legacy Resume vector-store helper.
 *
 * Resume retrieval no longer runs at application startup.  The old implementation
 * deleted vector_store on every boot, which made the table look like a source of
 * truth while it was actually only a disposable projection of about-mac.md.
 * Keep the batch helper for legacy/manual tests, but never invoke it from startup.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);
    private static final int EMBEDDING_BATCH_SIZE = 10;

    private final VectorStore vectorStore;
    public IngestService(VectorStore vectorStore,
                         JdbcTemplate jdbcTemplate,
                         KnowledgeDocumentChunker documentChunker, KnowledgeChunkStore chunkStore) {
        this.vectorStore = vectorStore;
    }

    void addInBatches(List<Document> chunks) {
        int batchCount = (chunks.size() + EMBEDDING_BATCH_SIZE - 1) / EMBEDDING_BATCH_SIZE;
        for (int start = 0, batchNumber = 1; start < chunks.size();
             start += EMBEDDING_BATCH_SIZE, batchNumber++) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, chunks.size());
            List<Document> batch = List.copyOf(chunks.subList(start, end));
            log.info("写入 Embedding 批次 {}/{}，chunk 数量：{}", batchNumber, batchCount, batch.size());
            vectorStore.add(batch);
        }
    }
}
