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
            statement.execute("DROP TABLE IF EXISTS bm25_context_fixture");
            try {
                statement.execute("CREATE TABLE bm25_context_fixture (chunk_id varchar(64) PRIMARY KEY, contextual_prefix text, index_content text, content text)");
                statement.execute("INSERT INTO bm25_context_fixture VALUES "
                        + "('exact-a', '', 'Error code TS-999 means the upload worker exhausted retry attempts.', 'Error code TS-999 means the upload worker exhausted retry attempts.'),"
                        + "('exact-b', '', 'Error code TS-998 means authentication failed.', 'Error code TS-998 means authentication failed.'),"
                        + "('general', '', 'General documentation about upload worker errors.', 'General documentation about upload worker errors.'),"
                        + "('acme', 'ACME Corporation Q2 2025 revenue performance.', 'ACME Corporation Q2 2025 revenue performance. Revenue increased by 3%.', 'Revenue increased by 3%.'),"
                        + "('beta', 'Beta Corporation Q1 2024 revenue performance.', 'Beta Corporation Q1 2024 revenue performance. Revenue increased by 8%.', 'Revenue increased by 8%.')");
                statement.execute("CREATE INDEX bm25_context_fixture_idx ON bm25_context_fixture "
                        + "USING bm25 ((chunk_id::pdb.literal), (index_content::pdb.icu)) WITH (key_field='chunk_id')");

                // Test 1：精确错误码 TS-999 必须排第一，证明不是 Java 手算或普通 LIKE。
                assertFirst(statement.executeQuery("SELECT chunk_id, pdb.score(chunk_id) AS score "
                        + "FROM bm25_context_fixture WHERE index_content ||| 'TS-999' "
                        + "ORDER BY pdb.score(chunk_id) DESC, chunk_id LIMIT 5"), "exact-a");

                // Test 2：ACME/Q2/2025 只存在 prefix，BM25 仍召回；content 保持原始可引用句子。
                try (ResultSet result = statement.executeQuery("SELECT chunk_id, content, pdb.score(chunk_id) AS score "
                        + "FROM bm25_context_fixture WHERE index_content ||| 'ACME Q2 2025 revenue' "
                        + "ORDER BY pdb.score(chunk_id) DESC, chunk_id LIMIT 5")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("chunk_id")).isEqualTo("acme");
                    assertThat(result.getString("content")).isEqualTo("Revenue increased by 3%.");
                    assertThat(result.getDouble("score")).isGreaterThan(0.0);
                }
            } finally {
                statement.execute("DROP TABLE IF EXISTS bm25_context_fixture");
            }
        }
    }

    private void assertFirst(ResultSet result, String expectedChunkId) throws Exception {
        try (result) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("chunk_id")).isEqualTo(expectedChunkId);
            assertThat(result.getDouble("score")).isGreaterThan(0.0);
        }
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
