package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;
import com.mac.portfolio.enterprise.repository.EnterpriseDocumentRepository;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL FTS 是 additive emergency fallback。
 * 这里保留现有 ts_rank_cd/search_vector，不把它冒充成 BM25。
 */
@Component
public class PostgresFtsLexicalRetriever implements EnterpriseLexicalRetriever {

    private final EnterpriseDocumentRepository repository;

    public PostgresFtsLexicalRetriever(EnterpriseDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public EnterpriseLexicalSearchResult search(String query, EnterpriseAccessContext access, int topK) {
        // 关键点：FTS 仍然复用原有 ACL、ACTIVE corpus、软删除过滤 SQL。
        return EnterpriseLexicalSearchResult.of(repository.searchKeyword(query, access, topK), "POSTGRES_FTS");
    }

    @Override
    public String configuredBackend() {
        return "POSTGRES_FTS";
    }

    @Override
    public EnterpriseLexicalHealth health() {
        // 主库健康由 EnterpriseCorpusService 检查；FTS 是同一个主库上的能力。
        return new EnterpriseLexicalHealth("POSTGRES_FTS", "POSTGRES_FTS", true, null);
    }
}
