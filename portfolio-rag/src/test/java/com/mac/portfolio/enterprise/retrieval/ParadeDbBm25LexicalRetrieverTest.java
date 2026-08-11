package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;
import com.mac.portfolio.enterprise.repository.EnterpriseDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
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

    @Test
    void healthExecutesRealBm25CapabilityProbeAfterIndexCheck() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(ParadeDbBm25LexicalRetriever.BM25_INDEX_NAME)))
                .thenReturn(1);
        when(jdbcTemplate.query(eq(ParadeDbBm25LexicalRetriever.BM25_HEALTH_SQL), any(org.springframework.jdbc.core.RowMapper.class),
                eq("__enterprise_bm25_healthcheck__"))).thenReturn(List.of());
        ParadeDbBm25LexicalRetriever retriever = new ParadeDbBm25LexicalRetriever(
                provider, mock(EnterpriseDocumentRepository.class), "jdbc:postgresql://search", 20, 3000, 0);

        EnterpriseLexicalHealth health = retriever.health();

        assertThat(health.healthy()).isTrue();
        verify(jdbcTemplate).query(eq(ParadeDbBm25LexicalRetriever.BM25_HEALTH_SQL),
                any(org.springframework.jdbc.core.RowMapper.class), eq("__enterprise_bm25_healthcheck__"));
    }

    @Test
    void healthReportsDownWhenPdbScoreCannotExecute() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(ParadeDbBm25LexicalRetriever.BM25_INDEX_NAME)))
                .thenReturn(1);
        when(jdbcTemplate.query(eq(ParadeDbBm25LexicalRetriever.BM25_HEALTH_SQL), any(org.springframework.jdbc.core.RowMapper.class),
                eq("__enterprise_bm25_healthcheck__")))
                .thenThrow(new DataAccessResourceFailureException("pdb.score failed"));
        ParadeDbBm25LexicalRetriever retriever = new ParadeDbBm25LexicalRetriever(
                provider, mock(EnterpriseDocumentRepository.class), "jdbc:postgresql://search", 20, 3000, 0);

        EnterpriseLexicalHealth health = retriever.health();

        assertThat(health.healthy()).isFalse();
        assertThat(health.reason()).isEqualTo("BM25_CAPABILITY_CHECK_FAILED");
    }
}
