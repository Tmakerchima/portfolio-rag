package com.mac.portfolio.enterprise.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;
import com.mac.portfolio.enterprise.model.EnterpriseChunk;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentRecord;
import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EnterpriseDocumentRepository {

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

    public Optional<EnterpriseDocumentRecord> findBySourceAndExternalId(String source, String externalId) {
        List<EnterpriseDocumentRecord> rows = jdbcTemplate.query("""
                SELECT document_id, external_id, source, content_hash, version, deleted_at
                FROM enterprise_documents
                WHERE corpus_id = ? AND source = ? AND external_id = ?
                """, (rs, rowNum) -> new EnterpriseDocumentRecord(
                rs.getString("document_id"),
                rs.getString("external_id"),
                rs.getString("source"),
                rs.getString("content_hash"),
                rs.getInt("version"),
                rs.getTimestamp("deleted_at") != null), LEGACY_CORPUS_ID, source, externalId);
        return rows.stream().findFirst();
    }

    public void upsertDocument(EnterpriseDocumentInput input, String documentId, String contentHash, int version) {
        jdbcTemplate.update("""
                INSERT INTO enterprise_documents
                    (corpus_id, document_id, external_id, source, source_type, title, content, content_hash,
                     version, tenant_id, department, access_level, metadata, updated_at, indexed_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now(), NULL)
                ON CONFLICT (corpus_id, source, external_id) DO UPDATE SET
                    document_id = EXCLUDED.document_id,
                    source_type = EXCLUDED.source_type,
                    title = EXCLUDED.title,
                    content = EXCLUDED.content,
                    content_hash = EXCLUDED.content_hash,
                    version = EXCLUDED.version,
                    tenant_id = EXCLUDED.tenant_id,
                    department = EXCLUDED.department,
                    access_level = EXCLUDED.access_level,
                    metadata = EXCLUDED.metadata,
                    updated_at = now(),
                    indexed_at = now(),
                    deleted_at = NULL
                """, LEGACY_CORPUS_ID, documentId, input.externalId(), input.source(), input.sourceType(), input.title(), input.content(),
                contentHash, version, input.tenantId(), input.department(), input.accessLevel(), toJson(input.metadata()));
    }

    public void deleteChunks(String documentId) {
        jdbcTemplate.update("DELETE FROM enterprise_chunks WHERE document_id = ?", documentId);
    }

    public void insertChunk(String documentId, EnterpriseChunk chunk, String contentHash,
                            Map<String, Object> metadata, float[] embedding) {
        jdbcTemplate.update("""
                INSERT INTO enterprise_chunks
                    (corpus_id, chunk_id, document_id, chunk_index, content, content_hash, token_count,
                     metadata, embedding, search_vector)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector, to_tsvector('simple', ?))
                """, LEGACY_CORPUS_ID, chunk.chunkId(), documentId, chunk.index(), chunk.content(), contentHash,
                estimateTokenCount(chunk.content()), toJson(metadata), vectorLiteral(embedding), chunk.content());
    }

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

    private EnterpriseSearchHit mapHit(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
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
        if (embedding == null || embedding.length == 0) throw new IllegalArgumentException("embedding is empty");
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) result.append(',');
            result.append(Float.toString(embedding[i]));
        }
        return result.append(']').toString();
    }

    private int estimateTokenCount(String content) {
        return Math.max(1, content.trim().isEmpty() ? 0 : content.trim().split("\\s+").length);
    }
}
