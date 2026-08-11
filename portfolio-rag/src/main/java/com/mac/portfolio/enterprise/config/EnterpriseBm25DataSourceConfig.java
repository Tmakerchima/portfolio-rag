package com.mac.portfolio.enterprise.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ParadeDB 使用独立连接配置，绝不把搜索副本和 Supabase 主库混成一个 DataSource。
 * 未选择 PARADEDB_BM25 时，这两个 Bean 都不会创建，因此普通开发环境无需 ParadeDB。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "enterprise.rag.lexical.backend", havingValue = "PARADEDB_BM25")
public class EnterpriseBm25DataSourceConfig {

    @Bean(name = "bm25DataSource", destroyMethod = "close")
    public HikariDataSource bm25DataSource(
            @Value("${enterprise.rag.bm25.url:}") String url,
            @Value("${enterprise.rag.bm25.username:}") String username,
            @Value("${enterprise.rag.bm25.password:}") String password,
            @Value("${enterprise.rag.bm25.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${enterprise.rag.bm25.maximum-pool-size:4}") int maximumPoolSize,
            @Value("${enterprise.rag.bm25.minimum-idle:0}") int minimumIdle) {
        // URL 为空时不在这里偷偷连接；BM25 adapter 会返回可观测的 CONFIGURATION_ERROR 并按 fail-open 降级。
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("paradedb-bm25-pool");
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(Math.max(1, maximumPoolSize));
        dataSource.setMinimumIdle(Math.min(Math.max(0, minimumIdle), dataSource.getMaximumPoolSize()));
        dataSource.setConnectionTimeout(Math.max(250, connectTimeoutMs));
        // 不在 Spring 启动时强制连通 ParadeDB；第一次查询失败时由 fail-open 产生明确 fallback。
        dataSource.setInitializationFailTimeout(-1);
        // PostgreSQL 驱动的 connectTimeout 单位为秒，限制一次新建物理连接的等待时间。
        dataSource.addDataSourceProperty("connectTimeout",
                Integer.toString(Math.max(1, (int) Math.ceil(connectTimeoutMs / 1000.0))));
        return dataSource;
    }

    @Bean(name = "bm25JdbcTemplate")
    public JdbcTemplate bm25JdbcTemplate(@Qualifier("bm25DataSource") DataSource bm25DataSource) {
        // 显式限定 bean 名，避免主库 JdbcTemplate 和 ParadeDB JdbcTemplate 注入歧义。
        return new JdbcTemplate(bm25DataSource);
    }
}
