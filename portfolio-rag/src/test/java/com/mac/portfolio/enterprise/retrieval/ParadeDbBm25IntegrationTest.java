package com.mac.portfolio.enterprise.retrieval;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真 ParadeDB 集成测试：只有显式设置 ENTERPRISE_BM25_INTEGRATION=true 才连接外部数据库。
 * 没有 Docker/连接信息时 Maven 默认跳过，不伪造 BM25 成功。
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "ENTERPRISE_BM25_INTEGRATION", matches = "true")
class ParadeDbBm25IntegrationTest {

    @Test
    void errorCodeAndContextualTermsAreRankedByParadeDb() throws Exception {
        String url = required("ENTERPRISE_RAG_BM25_URL");
        String user = System.getenv().getOrDefault("ENTERPRISE_RAG_BM25_USERNAME", "postgres");
        String password = System.getenv().getOrDefault("ENTERPRISE_RAG_BM25_PASSWORD", "postgres");
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE bm25_context_fixture (chunk_id varchar(64) PRIMARY KEY, index_content text, content text)");
            statement.execute("INSERT INTO bm25_context_fixture VALUES "
                    + "('a', 'Error code TS-999 means the upload worker exhausted retry attempts. ACME Q2 2025 revenue growth.', 'Error code TS-999 means the upload worker exhausted retry attempts.'),"
                    + "('b', 'Error code TS-998 means authentication failed. Beta Q1 2024 performance.', 'Error code TS-998 means authentication failed.'),"
                    + "('c', 'General documentation about upload worker errors.', 'General documentation about upload worker errors.')");
            statement.execute("CREATE INDEX bm25_context_fixture_idx ON bm25_context_fixture "
                    + "USING paradedb ((chunk_id::pdb.literal), (index_content::pdb.icu)) WITH (key_field='chunk_id')");

            try (ResultSet result = statement.executeQuery("SELECT chunk_id, pdb.score(chunk_id) AS score "
                    + "FROM bm25_context_fixture WHERE index_content ||| 'ACME Q2 2025 revenue growth' "
                    + "ORDER BY pdb.score(chunk_id) DESC, chunk_id LIMIT 5")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("chunk_id")).isEqualTo("a");
                assertThat(result.getDouble("score")).isGreaterThan(0.0);
            }
        }
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
