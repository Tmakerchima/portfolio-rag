package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** 根据 feature flag 选择 BM25，并在 fail-open 时回退到 PostgreSQL FTS。 */
@Component
@Primary
public class ConfigurableEnterpriseLexicalRetriever implements EnterpriseLexicalRetriever {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableEnterpriseLexicalRetriever.class);

    private final ParadeDbBm25LexicalRetriever bm25;
    private final PostgresFtsLexicalRetriever fts;
    private final Backend backend;
    private final boolean failOpen;

    public ConfigurableEnterpriseLexicalRetriever(
            ParadeDbBm25LexicalRetriever bm25,
            PostgresFtsLexicalRetriever fts,
            @Value("${enterprise.rag.lexical.backend:POSTGRES_FTS}") String backend,
            @Value("${enterprise.rag.lexical.fail-open:true}") boolean failOpen) {
        this.bm25 = bm25;
        this.fts = fts;
        this.backend = Backend.parse(backend);
        this.failOpen = failOpen;
    }

    @Override
    public EnterpriseLexicalSearchResult search(String query, EnterpriseAccessContext access, int topK) {
        if (backend == Backend.POSTGRES_FTS) return fts.search(query, access, topK);
        try {
            return bm25.search(query, access, topK);
        } catch (EnterpriseBm25UnavailableException error) {
            return fallbackOrThrow(query, access, topK, error.code(), error);
        } catch (RuntimeException error) {
            return fallbackOrThrow(query, access, topK, "BM25_UNAVAILABLE", error);
        }
    }

    private EnterpriseLexicalSearchResult fallbackOrThrow(String query, EnterpriseAccessContext access, int topK,
                                                           String reason, RuntimeException error) {
        if (!failOpen) {
            log.error("ParadeDB BM25 is unavailable and lexical.fail-open=false: {}", reason);
            throw error;
        }
        log.warn("ParadeDB BM25 unavailable; using PostgreSQL FTS fallback, reason={}", reason);
        return EnterpriseLexicalSearchResult.fallback(fts.search(query, access, topK).hits(),
                "POSTGRES_FTS_FALLBACK", "POSTGRES_FTS_FALLBACK:" + reason);
    }

    @Override
    public String configuredBackend() {
        return backend.name();
    }

    @Override
    public EnterpriseLexicalHealth health() {
        if (backend == Backend.POSTGRES_FTS) return fts.health();
        EnterpriseLexicalHealth bm25Health = bm25.health();
        if (bm25Health.healthy()) return bm25Health;
        if (failOpen) {
            return new EnterpriseLexicalHealth("PARADEDB_BM25", "POSTGRES_FTS_FALLBACK", false,
                    bm25Health.reason() == null ? "BM25_UNAVAILABLE" : bm25Health.reason());
        }
        return bm25Health;
    }

    enum Backend {
        POSTGRES_FTS, PARADEDB_BM25;

        static Backend parse(String value) {
            try {
                return value == null ? POSTGRES_FTS : valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 未知配置安全回到旧 FTS，防止升级后应用因为拼写错误直接启动失败。
                return POSTGRES_FTS;
            }
        }
    }
}
