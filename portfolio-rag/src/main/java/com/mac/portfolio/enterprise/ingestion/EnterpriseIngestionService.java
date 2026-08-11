package com.mac.portfolio.enterprise.ingestion;

import com.mac.portfolio.enterprise.model.EnterpriseChunk;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentRecord;
import com.mac.portfolio.enterprise.model.EnterpriseIndexedChunk;
import com.mac.portfolio.enterprise.repository.EnterpriseDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final int EMBEDDING_BATCH_SIZE = 10;

    private final EnterpriseDocumentRepository repository;
    private final EnterpriseDocumentChunker chunker;
    private final EnterpriseChunkContextualizer contextualizer;
    private final EmbeddingModel embeddingModel;
    private final int maxDocuments;
    private final String embeddingFingerprint;

    @Autowired
    public EnterpriseIngestionService(EnterpriseDocumentRepository repository,
                                      EnterpriseDocumentChunker chunker,
                                      EnterpriseChunkContextualizer contextualizer,
                                      EmbeddingModel embeddingModel,
                                      @Value("${enterprise.rag.max-documents:5000}") int maxDocuments,
                                      @Value("${enterprise.rag.embedding-model:text-embedding-v3}") String embeddingModelName,
                                      @Value("${enterprise.rag.embedding-dimensions:1024}") int embeddingDimensions) {
        this.repository = repository;
        this.chunker = chunker;
        this.contextualizer = contextualizer;
        this.embeddingModel = embeddingModel;
        this.maxDocuments = maxDocuments;
        this.embeddingFingerprint = "embedding:" + embeddingModelName + ":" + embeddingDimensions;
    }

    EnterpriseIngestionService(EnterpriseDocumentRepository repository,
                               EnterpriseDocumentChunker chunker,
                               EnterpriseChunkContextualizer contextualizer,
                               EmbeddingModel embeddingModel,
                               int maxDocuments) {
        this(repository, chunker, contextualizer, embeddingModel, maxDocuments, "test-model", 0);
    }

    @Transactional
    public IngestionResult ingest(EnterpriseDocumentInput input) {
        String normalizedContent = EnterpriseDocumentChunker.normalize(input.content());
        EnterpriseDocumentInput normalized = new EnterpriseDocumentInput(
                input.externalId(), input.source(), input.sourceType(), input.title(), normalizedContent,
                input.tenantId(), input.department(), input.accessLevel(), input.metadata(), input.sourceUpdatedAt());
        String contentHash = EnterpriseDocumentChunker.sha256(normalizedContent);
        String indexFingerprint = EnterpriseDocumentChunker.sha256(
                chunker.fingerprint() + ":" + contextualizer.fingerprint() + ":" + embeddingFingerprint);
        EnterpriseDocumentRecord existing = repository
                .findBySourceAndExternalId(normalized.source(), normalized.externalId())
                .orElse(null);

        if (existing != null && existing.contentHash().equals(contentHash)
                && existing.indexFingerprint().equals(indexFingerprint) && !existing.deleted()) {
            return new IngestionResult(existing.documentId(), 0, IngestionStatus.SKIPPED_UNCHANGED, existing.version());
        }

        List<EnterpriseChunk> chunks = chunker.chunk(normalized);
        if (chunks.isEmpty()) throw new IllegalArgumentException("Enterprise document content must not be blank");
        List<EnterpriseIndexedChunk> indexedChunks = chunks.stream()
                .map(chunk -> contextualizer.contextualize(normalized, chunk))
                .toList();
        List<float[]> embeddings = embedInBatches(indexedChunks);
        if (embeddings.size() != chunks.size()) {
            throw new IllegalStateException("Embedding provider returned a different number of vectors");
        }

        String documentId = existing == null ? stableDocumentId(normalized) : existing.documentId();
        int version = existing == null ? 1 : existing.version() + 1;
        // Embedding is computed before this mutation. If it fails, the transaction leaves an existing
        // document and its chunks intact; changed documents are never half-indexed.
        repository.upsertDocument(normalized, documentId, contentHash, indexFingerprint, version);
        repository.deleteChunks(documentId);
        for (int i = 0; i < chunks.size(); i++) {
            EnterpriseIndexedChunk indexedChunk = indexedChunks.get(i);
            EnterpriseChunk chunk = indexedChunk.chunk();
            Map<String, Object> metadata = Map.ofEntries(
                    Map.entry("source", normalized.source()),
                    Map.entry("source_type", normalized.sourceType()),
                    Map.entry("title", normalized.title()),
                    Map.entry("external_id", normalized.externalId()),
                    Map.entry("tenant_id", normalized.tenantId()),
                    Map.entry("department", normalized.department()),
                    Map.entry("access_level", normalized.accessLevel()),
                    Map.entry("chunk_index", chunk.index()),
                    Map.entry("section_path", chunk.sectionPath()),
                    Map.entry("chunk_tokens", chunk.tokenCount()),
                    Map.entry("contextualized", !indexedChunk.contextualPrefix().isBlank()));
            repository.insertChunk(documentId, indexedChunk, EnterpriseDocumentChunker.sha256(chunk.content()), metadata,
                    embeddings.get(i));
        }
        IngestionStatus status = existing == null ? IngestionStatus.INDEXED_NEW
                : existing.contentHash().equals(contentHash)
                ? IngestionStatus.REINDEXED_PIPELINE_CHANGED : IngestionStatus.REINDEXED_CHANGED;
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

    private List<float[]> embedInBatches(List<EnterpriseIndexedChunk> chunks) {
        List<float[]> embeddings = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, chunks.size());
            embeddings.addAll(embeddingModel.embed(chunks.subList(start, end).stream()
                    .map(EnterpriseIndexedChunk::indexContent).toList()));
        }
        return embeddings;
    }

    public enum IngestionStatus {
        INDEXED_NEW,
        REINDEXED_CHANGED,
        REINDEXED_PIPELINE_CHANGED,
        SKIPPED_UNCHANGED
    }

    public record IngestionResult(String documentId, int chunkCount, IngestionStatus status, int version) {}

    public record BatchIngestionResult(int received, int indexed, int skipped, List<IngestionResult> results) {}
}
