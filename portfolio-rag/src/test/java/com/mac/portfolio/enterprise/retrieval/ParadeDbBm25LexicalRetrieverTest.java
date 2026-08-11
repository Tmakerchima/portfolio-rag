package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;
import com.mac.portfolio.enterprise.repository.EnterpriseDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParadeDbBm25LexicalRetrieverTest {

    @Test
    void sqlUsesParadeDbBm25AndNeverRenamesPostgresFts() {
        assertThat(ParadeDbBm25LexicalRetriever.BM25_SQL)
                .contains("index_content ||| ?", "pdb.score(c.chunk_id)", "LIMIT ?",
                        "d.deleted_at IS NULL", "state = 'ACTIVE'", "d.access_level", "d.department", "d.tenant_id")
                .doesNotContain("ts_rank", "search_vector");
    }

    @Test
    void missingConnectionProducesStructuredConfigurationError() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        ParadeDbBm25LexicalRetriever retriever = new ParadeDbBm25LexicalRetriever(
                provider, mock(EnterpriseDocumentRepository.class), "", 20, 3000, 0);

        assertThatThrownBy(() -> retriever.search("TS-999", EnterpriseAccessContext.from("admin", null), 5))
                .isInstanceOf(EnterpriseBm25UnavailableException.class)
                .extracting("code")
                .isEqualTo("BM25_CONFIGURATION_ERROR");
    }
}
