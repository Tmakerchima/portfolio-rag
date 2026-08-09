package com.mac.portfolio.enterprise.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Blue/green corpus lifecycle. It changes only a pointer/state, never active rows. */
@Service
public class EnterpriseCorpusService {

    private final JdbcTemplate jdbcTemplate;

    public EnterpriseCorpusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> stats() {
        Map<String, Object> body = new LinkedHashMap<>();
        var corpus = jdbcTemplate.query("""
                SELECT corpus_id, dataset_name, dataset_version, state, embedding_provider,
                       embedding_model, embedding_dimension, chunker_version, expected_documents,
                       created_at, activated_at
                FROM enterprise_corpora
                WHERE state <> 'RETIRED'
                ORDER BY CASE WHEN state = 'ACTIVE' THEN 0 ELSE 1 END,
                         activated_at DESC NULLS LAST, created_at DESC
                LIMIT 1
                """, rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("corpus_id", rs.getObject("corpus_id", UUID.class));
                    value.put("dataset_name", rs.getString("dataset_name"));
                    value.put("dataset_version", rs.getString("dataset_version"));
                    value.put("state", rs.getString("state"));
                    value.put("embedding_provider", rs.getString("embedding_provider"));
                    value.put("embedding_model", rs.getString("embedding_model"));
                    value.put("embedding_dimension", rs.getInt("embedding_dimension"));
                    value.put("chunker_version", rs.getString("chunker_version"));
                    value.put("expected_documents", rs.getLong("expected_documents"));
                    value.put("created_at", rs.getObject("created_at"));
                    value.put("activated_at", rs.getObject("activated_at"));
                    return value;
                });

        if (corpus == null) {
            body.put("status", "EMPTY");
            body.put("active_corpus_id", null);
            body.put("expected_documents", 0L);
            body.put("document_count", 0L);
            body.put("chunk_count", 0L);
            body.put("embedded_chunk_count", 0L);
            body.put("failed_count", 0L);
            body.put("message", "Enterprise schema exists but no corpus is active");
            return body;
        }

        UUID corpusId = (UUID) corpus.get("corpus_id");
        body.putAll(corpus);
        boolean active = "ACTIVE".equals(corpus.get("state"));
        body.put("active_corpus_id", active ? corpusId : null);
        body.put("staging_corpus_id", active ? null : corpusId);
        body.put("staging_state", active ? null : corpus.get("state"));
        body.put("document_count", count("SELECT count(*) FROM enterprise_documents WHERE corpus_id = ? AND deleted_at IS NULL", corpusId));
        body.put("chunk_count", count("SELECT count(*) FROM enterprise_chunks WHERE corpus_id = ?", corpusId));
        body.put("embedded_chunk_count", count("SELECT count(*) FROM enterprise_chunks WHERE corpus_id = ? AND embedding IS NOT NULL", corpusId));
        body.put("failed_count", count("""
                SELECT coalesce(sum(failed_count), 0) FROM enterprise_ingestion_jobs WHERE corpus_id = ?
                """, corpusId));
        Map<String, Long> sourceDistribution = new LinkedHashMap<>();
        jdbcTemplate.queryForList("""
                SELECT source_type, count(*) AS total
                FROM enterprise_documents
                WHERE corpus_id = ? AND deleted_at IS NULL
                GROUP BY source_type ORDER BY source_type
                """, corpusId).forEach(row -> sourceDistribution.put(
                String.valueOf(row.get("source_type")), ((Number) row.get("total")).longValue()));
        body.put("source_distribution", sourceDistribution);
        body.put("status", active
                ? (body.get("document_count") instanceof Long count && count > 0 ? "READY" : "EMPTY")
                : "INGESTING");
        body.put("vector_backend", body.get("embedded_chunk_count") instanceof Long count && count > 0 ? "PGVECTOR" : "FTS_ONLY");
        body.put("fts_ready", true);
        body.put("vector_ready", body.get("embedded_chunk_count") instanceof Long count && count > 0);
        body.put("benchmark", Map.of("status", "NOT_MEASURED_YET"));
        return body;
    }

    public UUID create(String datasetName, String datasetVersion, long expectedDocuments,
                       String embeddingProvider, String embeddingModel, int dimension,
                       String chunkerVersion) {
        UUID corpusId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO enterprise_corpora
                    (corpus_id, dataset_name, dataset_version, state, expected_documents,
                     embedding_provider, embedding_model, embedding_dimension, chunker_version)
                VALUES (?, ?, ?, 'STAGING', ?, ?, ?, ?, ?)
                """, corpusId, datasetName, datasetVersion, expectedDocuments,
                embeddingProvider == null ? "" : embeddingProvider,
                embeddingModel == null ? "" : embeddingModel, dimension,
                chunkerVersion == null ? "" : chunkerVersion);
        return corpusId;
    }

    @Transactional
    public void activate(UUID corpusId) {
        requireCorpus(corpusId);
        jdbcTemplate.update("""
                UPDATE enterprise_corpora
                SET state = 'RETIRED', retired_at = now()
                WHERE state = 'ACTIVE' AND corpus_id <> ?
                """, corpusId);
        int updated = jdbcTemplate.update("""
                UPDATE enterprise_corpora SET state = 'ACTIVE', activated_at = now(), retired_at = NULL
                WHERE corpus_id = ? AND state IN ('READY', 'VALIDATING', 'ACTIVE')
                """, corpusId);
        if (updated != 1) throw new IllegalStateException("Corpus is not READY/VALIDATING: " + corpusId);
    }

    @Transactional
    public void rollback(UUID corpusId) {
        requireCorpus(corpusId);
        jdbcTemplate.update("UPDATE enterprise_corpora SET state = 'RETIRED', retired_at = now() WHERE state = 'ACTIVE'");
        int updated = jdbcTemplate.update("""
                UPDATE enterprise_corpora SET state = 'ACTIVE', activated_at = now(), retired_at = NULL
                WHERE corpus_id = ? AND state IN ('RETIRED', 'READY')
                """, corpusId);
        if (updated != 1) throw new IllegalStateException("Corpus cannot be activated for rollback: " + corpusId);
    }

    public void setState(UUID corpusId, String state) {
        int updated = jdbcTemplate.update("UPDATE enterprise_corpora SET state = ?, retired_at = CASE WHEN ? = 'RETIRED' THEN now() ELSE retired_at END WHERE corpus_id = ?",
                state, state, corpusId);
        if (updated != 1) throw new IllegalArgumentException("Unknown corpus: " + corpusId);
    }

    private void requireCorpus(UUID corpusId) {
        Long count = corpusId == null ? 0L : jdbcTemplate.queryForObject(
                "SELECT count(*) FROM enterprise_corpora WHERE corpus_id = ?", Long.class, corpusId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Unknown corpus: " + corpusId);
        }
    }

    private long count(String sql, UUID corpusId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, corpusId);
        return value == null ? 0 : value;
    }
}
