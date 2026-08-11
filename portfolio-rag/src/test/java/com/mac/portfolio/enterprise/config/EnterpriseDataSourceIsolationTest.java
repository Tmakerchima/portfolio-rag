package com.mac.portfolio.enterprise.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.portfolio.config.PrimaryDataSourceConfig;
import com.mac.portfolio.enterprise.repository.EnterpriseDocumentRepository;
import com.mac.portfolio.enterprise.retrieval.ParadeDbBm25LexicalRetriever;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class EnterpriseDataSourceIsolationTest {

    @Test
    void repositoriesUsePrimaryWhileBm25RetrieverUsesQualifiedSearchPool() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(
                    "spring.datasource.url=jdbc:postgresql://localhost:5432/primary_db",
                    "spring.datasource.username=primary_user",
                    "spring.datasource.password=primary_password",
                    "spring.datasource.hikari.maximum-pool-size=3",
                    "spring.datasource.hikari.minimum-idle=0",
                    "enterprise.rag.lexical.backend=PARADEDB_BM25",
                    "enterprise.rag.bm25.url=jdbc:postgresql://localhost:5433/search_db",
                    "enterprise.rag.bm25.username=search_user",
                    "enterprise.rag.bm25.password=search_password",
                    "enterprise.rag.bm25.maximum-pool-size=2",
                    "enterprise.rag.bm25.minimum-idle=0"
            ).applyTo(context);
            context.register(PrimaryDataSourceConfig.class, EnterpriseBm25DataSourceConfig.class);
            context.registerBean("objectMapper", ObjectMapper.class, (Supplier<ObjectMapper>) ObjectMapper::new);
            context.registerBean(EnterpriseDocumentRepository.class);
            context.registerBean(ParadeDbBm25LexicalRetriever.class);
            context.refresh();

            HikariDataSource primary = context.getBean("primaryDataSource", HikariDataSource.class);
            HikariDataSource bm25 = context.getBean("bm25DataSource", HikariDataSource.class);
            JdbcTemplate primaryJdbc = context.getBean("primaryJdbcTemplate", JdbcTemplate.class);
            JdbcTemplate bm25Jdbc = context.getBean("bm25JdbcTemplate", JdbcTemplate.class);

            assertThat(primary).isNotSameAs(bm25);
            assertThat(primary.getJdbcUrl()).contains("primary_db");
            assertThat(bm25.getJdbcUrl()).contains("search_db");
            assertThat(context.getBean(JdbcTemplate.class)).isSameAs(primaryJdbc);
            assertThat(primaryJdbc.getDataSource()).isSameAs(primary);
            assertThat(bm25Jdbc.getDataSource()).isSameAs(bm25);

            EnterpriseDocumentRepository repository = context.getBean(EnterpriseDocumentRepository.class);
            assertThat(ReflectionTestUtils.getField(repository, "jdbcTemplate")).isSameAs(primaryJdbc);

            ParadeDbBm25LexicalRetriever retriever = context.getBean(ParadeDbBm25LexicalRetriever.class);
            ObjectProvider<?> provider = (ObjectProvider<?>) ReflectionTestUtils.getField(retriever, "jdbcTemplateProvider");
            assertThat(provider).isNotNull();
            assertThat(provider.getIfAvailable()).isSameAs(bm25Jdbc);
        }
    }
}
