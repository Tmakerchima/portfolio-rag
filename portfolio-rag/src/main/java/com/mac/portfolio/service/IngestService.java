package com.mac.portfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Legacy/manual Portfolio vector-store helper.
 *
 * The active Portfolio path loads knowledge documents into an in-memory chunk store
 * and never deletes or rebuilds vector_store on application startup. Keep this batch
 * helper for controlled embedding jobs and tests only.
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
