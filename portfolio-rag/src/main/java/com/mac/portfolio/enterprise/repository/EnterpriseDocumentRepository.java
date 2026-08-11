package com.mac.portfolio.enterprise.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;
import com.mac.portfolio.enterprise.model.EnterpriseChunk;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentRecord;
import com.mac.portfolio.enterprise.model.EnterpriseIndexedChunk;
import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enterprise 文档与 Chunk 的 PostgreSQL 持久化层。
 * 入库时同时保存原文、检索增强文本、pgvector 向量和 PostgreSQL 全文索引。
 */
@Repository
public class EnterpriseDocumentRepository {

    /** Java /ingest 兼容入口写入的固定 Corpus；大规模 Worker 使用独立代际 Corpus。 */
    public static final UUID LEGACY_CORPUS_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String ACL_FILTER = """
            AND (? = 'admin' OR d.access_level = 'public' OR d.department = ?)
            AND (CAST(? AS text) IS NULL OR d.tenant_id = CAST(? AS text))
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EnterpriseDocumentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** 使用 source + externalId 查找旧记录，供入库服务做内容和管线指纹比较。 */
    public Optional<EnterpriseDocumentRecord> findBySourceAndExternalId(String source, String externalId) {
        List<EnterpriseDocumentRecord> rows = jdbcTemplate.query("""
                SELECT document_id, external_id, source, content_hash, index_fingerprint, version, deleted_at
                FROM enterprise_documents
                WHERE corpus_id = ? AND source = ? AND external_id = ?
                """, (rs, rowNum) -> new EnterpriseDocumentRecord(
                rs.getString("document_id"),
                rs.getString("external_id"),
                rs.getString("source"),
                rs.getString("content_hash"),
                rs.getString("index_fingerprint"),
                rs.getInt("version"),
                rs.getTimestamp("deleted_at") != null), LEGACY_CORPUS_ID, source, externalId);
        return rows.stream().findFirst();
    }

    /**
     * 写入文档级原文和权限信息；已存在时更新同一条记录并清除 deleted_at，
     * 因此软删除后的文档再次入库可以恢复。
     */
    public void upsertDocument(EnterpriseDocumentInput input, String documentId, String contentHash,
                               String indexFingerprint, int version) {
        jdbcTemplate.update("""
                INSERT INTO enterprise_documents
                    (corpus_id, document_id, external_id, source, source_type, title, content, content_hash, index_fingerprint,
                     version, tenant_id, department, access_level, metadata, updated_at, indexed_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now(), NULL)
                ON CONFLICT (corpus_id, source, external_id) DO UPDATE SET
                    document_id = EXCLUDED.document_id,
                    source_type = EXCLUDED.source_type,
                    title = EXCLUDED.title,
                    content = EXCLUDED.content,
                    content_hash = EXCLUDED.content_hash,
                    index_fingerprint = EXCLUDED.index_fingerprint,
                    version = EXCLUDED.version,
                    tenant_id = EXCLUDED.tenant_id,
                    department = EXCLUDED.department,
                    access_level = EXCLUDED.access_level,
                    metadata = EXCLUDED.metadata,
                    updated_at = now(),
                    indexed_at = now(),
                    deleted_at = NULL
                """, LEGACY_CORPUS_ID, documentId, input.externalId(), input.source(), input.sourceType(), input.title(), input.content(),
                contentHash, indexFingerprint, version, input.tenantId(), input.department(), input.accessLevel(), toJson(input.metadata()));
    }

    /** 重建文档索引前删除旧 Chunk，随后由同一事务写入完整的新 Chunk 集合。 */
    public void deleteChunks(String documentId) {
        jdbcTemplate.update("DELETE FROM enterprise_chunks WHERE document_id = ?", documentId);
    }

    /**
     * 插入一个最终 Chunk：content 是可引用原文，indexContent 用于 Embedding 与 FTS，
     * contextualPrefix 单独保存，避免回答时把模型生成内容误当成来源证据。
     */
    public void insertChunk(String documentId, EnterpriseIndexedChunk indexedChunk, String contentHash,
                            Map<String, Object> metadata, float[] embedding) {
        EnterpriseChunk chunk = indexedChunk.chunk();
        jdbcTemplate.update("""
                INSERT INTO enterprise_chunks
                    (corpus_id, chunk_id, document_id, chunk_index, content, contextual_prefix, index_content,
                     content_hash, token_count, metadata, embedding, search_vector)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector, to_tsvector('simple', ?))
                """, LEGACY_CORPUS_ID, chunk.chunkId(), documentId, chunk.index(), chunk.content(),
                indexedChunk.contextualPrefix(), indexedChunk.indexContent(), contentHash,
                indexedChunk.indexTokenCount(), toJson(metadata), vectorLiteral(embedding), indexedChunk.indexContent());
    }

    /** 设置 deleted_at 完成软删除；查询 SQL 会过滤这些文档。 */
    public void softDelete(String source, String externalId) {
        jdbcTemplate.update("""
                UPDATE enterprise_documents
                SET deleted_at = now(), updated_at = now()
                WHERE source = ? AND external_id = ?
                """, source, externalId);
    }

    public List<EnterpriseSearchHit> searchKeyword(String query, EnterpriseAccessContext access, int topK) {
        String sql = """
                WITH query_text AS (
                    SELECT websearch_to_tsquery('simple', ?) AS query
                )
                SELECT c.chunk_id, c.document_id, d.external_id, d.source, d.source_type, d.title,
                       c.content, d.tenant_id, d.department, d.access_level, c.chunk_index,
                       ts_rank_cd(c.search_vector, query_text.query) AS score, c.metadata
                FROM enterprise_chunks c
                JOIN enterprise_documents d ON d.document_id = c.document_id
                CROSS JOIN query_text
                WHERE d.deleted_at IS NULL
                  AND d.corpus_id = (SELECT corpus_id FROM enterprise_corpora
                                     WHERE state = 'ACTIVE' LIMIT 1)
                  AND c.search_vector @@ query_text.query
                """ + ACL_FILTER + """
                ORDER BY score DESC, c.chunk_id
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, this::mapHit, query, access.role(), access.department(),
                access.tenantId(), access.tenantId(), topK).stream()
                .map(hit -> hit.withScore(hit.score(), hit.rank()))
                .toList();
    }

    public List<EnterpriseSearchHit> searchVector(float[] embedding, EnterpriseAccessContext access,
                                                  int topK, double threshold) {
        String sql = """
                WITH query_embedding AS (
                    SELECT ?::vector AS embedding
                )
                SELECT c.chunk_id, c.document_id, d.external_id, d.source, d.source_type, d.title,
                       c.content, d.tenant_id, d.department, d.access_level, c.chunk_index,
                       1 - (c.embedding <=> query_embedding.embedding) AS score, c.metadata
                FROM enterprise_chunks c
                JOIN enterprise_documents d ON d.document_id = c.document_id
                CROSS JOIN query_embedding
                WHERE d.deleted_at IS NULL
                  AND d.corpus_id = (SELECT corpus_id FROM enterprise_corpora
                                     WHERE state = 'ACTIVE' LIMIT 1)
                  AND c.embedding IS NOT NULL
                  AND 1 - (c.embedding <=> query_embedding.embedding) >= ?
                """ + ACL_FILTER + """
                ORDER BY c.embedding <=> query_embedding.embedding, c.chunk_id
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, this::mapHit, vectorLiteral(embedding), threshold, access.role(),
                access.department(), access.tenantId(), access.tenantId(), topK).stream()
                .map(hit -> hit.withScore(hit.score(), hit.rank()))
                .toList();
    }

    /**
     * 统一把 Supabase/ParadeDB 两个 lexical backend 的行映射成同一份安全结果。
     * BM25 数据源只负责执行搜索，原始 content 和 ACL 元数据仍使用同一映射规则。
     */
    public EnterpriseSearchHit mapHit(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new EnterpriseSearchHit(
                rs.getString("chunk_id"),
                rs.getString("document_id"),
                rs.getString("external_id"),
                rs.getString("source"),
                rs.getString("source_type"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("tenant_id"),
                rs.getString("department"),
                rs.getString("access_level"),
                rs.getInt("chunk_index"),
                rs.getDouble("score"),
                rowNum + 1,
                readMetadata(rs.getString("metadata")));
    }

    private String toJson(Map<String, Object> value) {
        try {
            // PostgreSQL metadata 字段是 jsonb，因此先把 Java Map 序列化为 JSON 字符串。
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Enterprise metadata is not JSON serializable", e);
        }
    }

    private Map<String, Object> readMetadata(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    static String vectorLiteral(float[] embedding) {
        // pgvector JDBC 参数使用 [0.1,0.2,...] 文本格式后再由 SQL 转成 vector。
        if (embedding == null || embedding.length == 0) throw new IllegalArgumentException("embedding is empty");
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) result.append(',');
            result.append(Float.toString(embedding[i]));
        }
        return result.append(']').toString();
    }

}
