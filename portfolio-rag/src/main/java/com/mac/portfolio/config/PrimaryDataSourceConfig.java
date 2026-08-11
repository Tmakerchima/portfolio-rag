package com.mac.portfolio.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Supabase 是应用的主数据库：文档、Chunk、corpus、ingestion 和 pgvector 都必须走这里。
 * 显式声明 dataSource/jdbcTemplate 为 @Primary，避免启用 ParadeDB 后 Spring 自动配置退让或注入歧义。
 */
@Configuration(proxyBeanMethods = false)
public class PrimaryDataSourceConfig {

    @Bean(name = {"dataSource", "primaryDataSource"}, destroyMethod = "close")
    @Primary
    public HikariDataSource primaryDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.datasource.hikari.maximum-pool-size:10}") int maximumPoolSize,
            @Value("${spring.datasource.hikari.minimum-idle:1}") int minimumIdle,
            @Value("${spring.datasource.hikari.connection-timeout:30000}") long connectionTimeoutMs) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("supabase-primary-pool");
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(Math.max(1, maximumPoolSize));
        dataSource.setMinimumIdle(Math.min(Math.max(0, minimumIdle), dataSource.getMaximumPoolSize()));
        dataSource.setConnectionTimeout(Math.max(250, connectionTimeoutMs));
        return dataSource;
    }

    @Bean(name = {"jdbcTemplate", "primaryJdbcTemplate"})
    @Primary
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        // 未写 qualifier 的 repository 会得到这个 @Primary JdbcTemplate，而不是 BM25 搜索副本。
        return new JdbcTemplate(primaryDataSource);
    }
}
