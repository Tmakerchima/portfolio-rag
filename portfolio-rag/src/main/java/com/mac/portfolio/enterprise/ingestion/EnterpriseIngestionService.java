package com.mac.portfolio.enterprise.ingestion;

import com.mac.portfolio.enterprise.model.EnterpriseChunk;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentRecord;
import com.mac.portfolio.enterprise.repository.EnterpriseDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EnterpriseIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseIngestionService.class);

    private final EnterpriseDocumentRepository repository;
    private final EnterpriseDocumentChunker chunker;
    private final EmbeddingModel embeddingModel;
    private final int maxDocuments;

    public EnterpriseIngestionService(EnterpriseDocumentRepository repository,
                                      EnterpriseDocumentChunker chunker,
                                      EmbeddingModel embeddingModel,
                                      @Value("${enterprise.rag.max-documents:5000}") int maxDocuments) {
        this.repository = repository;
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
        this.maxDocuments = maxDocuments;
    }

    @Transactional
    public IngestionResult ingest(EnterpriseDocumentInput input) {
        String normalizedContent = EnterpriseDocumentChunker.normalize(input.content());
        EnterpriseDocumentInput normalized = new EnterpriseDocumentInput(
                input.externalId(), input.source(), input.sourceType(), input.title(), normalizedContent,
                input.tenantId(), input.department(), input.accessLevel(), input.metadata(), input.sourceUpdatedAt());
        String contentHash = EnterpriseDocumentChunker.sha256(normalizedContent);
        EnterpriseDocumentRecord existing = repository
                .findBySourceAndExternalId(normalized.source(), normalized.externalId())
                .orElse(null);

        if (existing != null && existing.contentHash().equals(contentHash) && !existing.deleted()) {
            return new IngestionResult(existing.documentId(), 0, IngestionStatus.SKIPPED_UNCHANGED, existing.version());
        }

        List<EnterpriseChunk> chunks = chunker.chunk(normalized);
        if (chunks.isEmpty()) throw new IllegalArgumentException("Enterprise document content must not be blank");
        List<float[]> embeddings = embeddingModel.embed(chunks.stream().map(EnterpriseChunk::content).toList());
        if (embeddings.size() != chunks.size()) {
            throw new IllegalStateException("Embedding provider returned a different number of vectors");
        }

        String documentId = existing == null ? stableDocumentId(normalized) : existing.documentId();
        int version = existing == null ? 1 : existing.version() + 1;
        // Embedding is computed before this mutation. If it fails, the transaction leaves an existing
        // document and its chunks intact; changed documents are never half-indexed.
        repository.upsertDocument(normalized, documentId, contentHash, version);
        repository.deleteChunks(documentId);
        for (int i = 0; i < chunks.size(); i++) {
            EnterpriseChunk chunk = chunks.get(i);
            Map<String, Object> metadata = Map.of(
                    "source", normalized.source(),
                    "source_type", normalized.sourceType(),
                    "title", normalized.title(),
                    "external_id", normalized.externalId(),
                    "tenant_id", normalized.tenantId(),
                    "department", normalized.department(),
                    "access_level", normalized.accessLevel(),
                    "chunk_index", chunk.index());
            repository.insertChunk(documentId, chunk, EnterpriseDocumentChunker.sha256(chunk.content()), metadata,
                    embeddings.get(i));
        }
        IngestionStatus status = existing == null ? IngestionStatus.INDEXED_NEW : IngestionStatus.REINDEXED_CHANGED;
        log.info("Enterprise document indexed: source={}, externalId={}, version={}, chunks={}",
                normalized.source(), normalized.externalId(), version, chunks.size());
        return new IngestionResult(documentId, chunks.size(), status, version);
    }

    @Transactional
    public BatchIngestionResult ingestBatch(List<EnterpriseDocumentInput> documents) {
        if (documents == null || documents.isEmpty()) return new BatchIngestionResult(0, 0, 0, List.of());
        if (documents.size() > maxDocuments) {
            throw new IllegalArgumentException("Batch exceeds ENTERPRISE_RAG_MAX_DOCUMENTS=" + maxDocuments);
        }
        int indexed = 0;
        int skipped = 0;
        List<IngestionResult> results = new ArrayList<>();
        for (EnterpriseDocumentInput document : documents) {
            IngestionResult result = ingest(document);
            results.add(result);
            if (result.status() == IngestionStatus.SKIPPED_UNCHANGED) skipped++;
            else indexed++;
        }
        return new BatchIngestionResult(documents.size(), indexed, skipped, List.copyOf(results));
    }

    @Transactional
    public void delete(String source, String externalId) {
        repository.softDelete(source, externalId);
    }

    private String stableDocumentId(EnterpriseDocumentInput input) {
        return UUID.nameUUIDFromBytes((input.source() + ":" + input.externalId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    public enum IngestionStatus {
        INDEXED_NEW,
        REINDEXED_CHANGED,
        SKIPPED_UNCHANGED
    }

    public record IngestionResult(String documentId, int chunkCount, IngestionStatus status, int version) {}

    public record BatchIngestionResult(int received, int indexed, int skipped, List<IngestionResult> results) {}
}
