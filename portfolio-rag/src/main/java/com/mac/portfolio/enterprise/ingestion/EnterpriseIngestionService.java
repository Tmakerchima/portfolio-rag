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

/**
 * Enterprise 文档入库的编排服务。
 *
 * <p>它不负责解析 PDF/JPG 文件，而是接收已经提取成文本的 EnterpriseDocumentInput，
 * 依次完成标准化、增量判断、切块、上下文增强、Embedding 和事务写库。</p>
 */
@Service
public class EnterpriseIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseIngestionService.class);
    /** 限制单次 Embedding 请求的文本数量，避免请求体过大或触发供应商限流。 */
    private static final int EMBEDDING_BATCH_SIZE = 10;

    private final EnterpriseDocumentRepository repository;
    private final EnterpriseDocumentChunker chunker;
    private final EnterpriseChunkContextualizer contextualizer;
    private final EmbeddingModel embeddingModel;
    private final int maxDocuments;
    /** Embedding 模型或维度改变时，指纹会改变，从而强制重建旧文档索引。 */
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

    /** 在一个事务中处理单份文档；异常时不会留下只写了一半的新 Chunk。 */
    @Transactional
    public IngestionResult ingest(EnterpriseDocumentInput input) {
        // 步骤 1：统一换行、BOM 和行尾空格，避免格式差异导致无意义的重复入库。
        String normalizedContent = EnterpriseDocumentChunker.normalize(input.content());
        EnterpriseDocumentInput normalized = new EnterpriseDocumentInput(
                input.externalId(), input.source(), input.sourceType(), input.title(), normalizedContent,
                input.tenantId(), input.department(), input.accessLevel(), input.metadata(), input.sourceUpdatedAt());

        // 步骤 2：分别计算“文档内容”和“索引处理管线”的指纹。
        // 内容没变但切块参数、上下文模型或 Embedding 配置变了，也必须重新建立索引。
        String contentHash = EnterpriseDocumentChunker.sha256(normalizedContent);
        String indexFingerprint = EnterpriseDocumentChunker.sha256(
                chunker.fingerprint() + ":" + contextualizer.fingerprint() + ":" + embeddingFingerprint);

        // 步骤 3：使用 source + externalId 查询同一来源文档是否已经存在。
        EnterpriseDocumentRecord existing = repository
                .findBySourceAndExternalId(normalized.source(), normalized.externalId())
                .orElse(null);

        // 文档内容、索引配置都没变且没有被软删除时，直接跳过昂贵的切块和 Embedding。
        if (existing != null && existing.contentHash().equals(contentHash)
                && existing.indexFingerprint().equals(indexFingerprint) && !existing.deleted()) {
            return new IngestionResult(existing.documentId(), 0, IngestionStatus.SKIPPED_UNCHANGED, existing.version());
        }

        // 步骤 4：先产出保持原文的 Chunk，再选择性生成检索专用的上下文前缀。
        List<EnterpriseChunk> chunks = chunker.chunk(normalized);
        if (chunks.isEmpty()) throw new IllegalArgumentException("Enterprise document content must not be blank");
        List<EnterpriseIndexedChunk> indexedChunks = chunks.stream()
                .map(chunk -> contextualizer.contextualize(normalized, chunk))
                .toList();

        // 步骤 5：对 indexContent（可能是“上下文前缀 + 原文”）生成向量。
        List<float[]> embeddings = embedInBatches(indexedChunks);
        if (embeddings.size() != chunks.size()) {
            throw new IllegalStateException("Embedding provider returned a different number of vectors");
        }

        // 新文档使用稳定 ID；旧文档沿用原 ID。每次有效重建都会递增版本号。
        String documentId = existing == null ? stableDocumentId(normalized) : existing.documentId();
        int version = existing == null ? 1 : existing.version() + 1;

        // 步骤 6：Embedding 成功后才开始改数据库。
        // 如果外部 Embedding 调用失败，旧文档和旧 Chunk 仍保持完整，不会出现半套索引。
        repository.upsertDocument(normalized, documentId, contentHash, indexFingerprint, version);

        // 同一文档采用“删旧 Chunk、插入全套新 Chunk”的方式保证顺序、内容和向量一致。
        repository.deleteChunks(documentId);
        for (int i = 0; i < chunks.size(); i++) {
            EnterpriseIndexedChunk indexedChunk = indexedChunks.get(i);
            EnterpriseChunk chunk = indexedChunk.chunk();

            // 查询结果需要这些元数据来展示来源、章节并执行租户/部门权限判断。
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

        // 区分新文档、正文变化和处理管线变化，方便导入任务统计真实原因。
        IngestionStatus status = existing == null ? IngestionStatus.INDEXED_NEW
                : existing.contentHash().equals(contentHash)
                ? IngestionStatus.REINDEXED_PIPELINE_CHANGED : IngestionStatus.REINDEXED_CHANGED;
        log.info("Enterprise document indexed: source={}, externalId={}, version={}, chunks={}",
                normalized.source(), normalized.externalId(), version, chunks.size());
        return new IngestionResult(documentId, chunks.size(), status, version);
    }

    /** 批量入口：限制一次 HTTP 请求的文档数，并逐份收集成功/跳过结果。 */
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
            // 调用单文档方法，复用相同的去重、切块、Embedding 和写库规则。
            IngestionResult result = ingest(document);
            results.add(result);
            if (result.status() == IngestionStatus.SKIPPED_UNCHANGED) skipped++;
            else indexed++;
        }
        return new BatchIngestionResult(documents.size(), indexed, skipped, List.copyOf(results));
    }

    /** 删除采用软删除：保留文档记录，只让查询不再召回它。 */
    @Transactional
    public void delete(String source, String externalId) {
        repository.softDelete(source, externalId);
    }

    private String stableDocumentId(EnterpriseDocumentInput input) {
        // 相同 source + externalId 总能计算出相同 UUID，重复导入不会制造新的文档身份。
        return UUID.nameUUIDFromBytes((input.source() + ":" + input.externalId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private List<float[]> embedInBatches(List<EnterpriseIndexedChunk> chunks) {
        List<float[]> embeddings = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, chunks.size());
            // 这里故意使用 indexContent，而不是可引用原文 chunk.content。
            embeddings.addAll(embeddingModel.embed(chunks.subList(start, end).stream()
                    .map(EnterpriseIndexedChunk::indexContent).toList()));
        }
        return embeddings;
    }

    /** 单份文档本次入库的状态。 */
    public enum IngestionStatus {
        INDEXED_NEW,                 // 数据库中此前不存在该文档
        REINDEXED_CHANGED,           // 来源正文发生变化
        REINDEXED_PIPELINE_CHANGED,  // 正文没变，但切块/上下文/Embedding 配置变化
        SKIPPED_UNCHANGED            // 正文和索引管线都没变
    }

    /** 单份文档的返回结果。 */
    public record IngestionResult(String documentId, int chunkCount, IngestionStatus status, int version) {}

    /** 整批请求的汇总结果。 */
    public record BatchIngestionResult(int received, int indexed, int skipped, List<IngestionResult> results) {}
}
