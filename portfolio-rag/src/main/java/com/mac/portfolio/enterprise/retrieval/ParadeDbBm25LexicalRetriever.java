package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;
import com.mac.portfolio.enterprise.repository.EnterpriseDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 真正的 ParadeDB lexical backend。
 *
 * <p>BM25 评分在 ParadeDB 索引内完成：查询必须出现 {@code |||}，
 * 分数必须来自 {@code pdb.score(...)}。因此这里绝不能复用
 * PostgreSQL 的 {@code ts_rank_cd}。</p>
 */
@Component
public class ParadeDbBm25LexicalRetriever implements EnterpriseLexicalRetriever {

    /** deploy/paradedb/03_paradedb_bm25_index.sql 中固定的 index 名，用于健康检查。 */
    static final String BM25_INDEX_NAME = "enterprise_chunks_index_content_paradedb_idx";

    /** 用于单元测试和代码审查的 SQL 证据：没有 ts_rank_cd，只有 ParadeDB BM25 operator/score。 */
    static final String BM25_SQL = """
            SELECT c.chunk_id, c.document_id, d.external_id, d.source, d.source_type, d.title,
                   c.content, d.tenant_id, d.department, d.access_level, c.chunk_index,
                   pdb.score(c.chunk_id) AS score, c.metadata
            FROM enterprise_chunks c
            JOIN enterprise_documents d ON d.document_id = c.document_id AND d.corpus_id = c.corpus_id
            WHERE c.index_content ||| ?
              AND d.deleted_at IS NULL
              AND d.corpus_id = (SELECT corpus_id FROM enterprise_corpora
                                 WHERE state = 'ACTIVE' LIMIT 1)
              AND c.index_content IS NOT NULL
              AND (? = 'admin' OR d.access_level = 'public' OR d.department = ?)
              AND (CAST(? AS text) IS NULL OR d.tenant_id = CAST(? AS text))
            ORDER BY pdb.score(c.chunk_id) DESC, c.chunk_id ASC
            LIMIT ?
            """;

    private static final Logger log = LoggerFactory.getLogger(ParadeDbBm25LexicalRetriever.class);

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final EnterpriseDocumentRepository hitMapper;
    private final String url;
    private final int bm25TopK;
    private final int queryTimeoutMs;
    private final int maxRetries;

    public ParadeDbBm25LexicalRetriever(
            @Qualifier("bm25JdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            EnterpriseDocumentRepository hitMapper,
            @Value("${enterprise.rag.bm25.url:}") String url,
            @Value("${enterprise.rag.bm25.top-k:20}") int bm25TopK,
            @Value("${enterprise.rag.bm25.query-timeout-ms:3000}") int queryTimeoutMs,
            @Value("${enterprise.rag.bm25.max-retries:0}") int maxRetries) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.hitMapper = hitMapper;
        this.url = url == null ? "" : url.trim();
        this.bm25TopK = Math.max(1, bm25TopK);
        this.queryTimeoutMs = Math.max(100, queryTimeoutMs);
        this.maxRetries = Math.min(2, Math.max(0, maxRetries));
    }

    @Override
    public EnterpriseLexicalSearchResult search(String query, EnterpriseAccessContext access, int topK) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null || url.isBlank()) {
            throw new EnterpriseBm25UnavailableException("BM25_CONFIGURATION_ERROR",
                    "ParadeDB BM25 backend is selected but enterprise.rag.bm25.url is not configured");
        }
        int boundedTopK = Math.min(Math.max(1, topK), bm25TopK);
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                // JdbcTemplate 的参数绑定会把用户查询作为值传入，避免把特殊查询语法拼进 SQL。
                jdbcTemplate.setQueryTimeout(Math.max(1, (int) Math.ceil(queryTimeoutMs / 1000.0)));
                var hits = jdbcTemplate.query(BM25_SQL, hitMapper::mapHit, query,
                        access.role(), access.department(), access.tenantId(), access.tenantId(), boundedTopK);
                return EnterpriseLexicalSearchResult.of(hits, "PARADEDB_BM25");
            } catch (DataAccessException error) {
                if (attempt < maxRetries) {
                    log.warn("ParadeDB BM25 query failed; bounded retry {}/{}: {}",
                            attempt + 1, maxRetries, error.getMessage());
                    continue;
                }
                log.warn("ParadeDB BM25 query failed: {}", error.getMessage());
                throw new EnterpriseBm25UnavailableException("BM25_QUERY_FAILED", "ParadeDB BM25 query failed", error);
            }
        }
        throw new EnterpriseBm25UnavailableException("BM25_QUERY_FAILED", "ParadeDB BM25 query failed");
    }

    @Override
    public String configuredBackend() {
        return "PARADEDB_BM25";
    }

    @Override
    public EnterpriseLexicalHealth health() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null || url.isBlank()) {
            return new EnterpriseLexicalHealth("PARADEDB_BM25", "PARADEDB_BM25", false,
                    "BM25_CONFIGURATION_ERROR");
        }
        try {
            jdbcTemplate.setQueryTimeout(Math.max(1, (int) Math.ceil(queryTimeoutMs / 1000.0)));
            Integer indexPresent = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_class WHERE relname = ?", Integer.class, BM25_INDEX_NAME);
            if (indexPresent == null || indexPresent == 0) {
                return new EnterpriseLexicalHealth("PARADEDB_BM25", "PARADEDB_BM25", false,
                        "BM25_INDEX_MISSING");
            }
            return new EnterpriseLexicalHealth("PARADEDB_BM25", "PARADEDB_BM25", true, null);
        } catch (DataAccessException error) {
            log.warn("ParadeDB BM25 health check failed: {}", error.getMessage());
            return new EnterpriseLexicalHealth("PARADEDB_BM25", "PARADEDB_BM25", false,
                    "BM25_UNAVAILABLE");
        }
    }
}

/** 让路由层识别配置错误、连接失败和超时，并产生结构化 fallback reason。 */
class EnterpriseBm25UnavailableException extends RuntimeException {
    private final String code;

    EnterpriseBm25UnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    EnterpriseBm25UnavailableException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    String code() {
        return code;
    }
}
