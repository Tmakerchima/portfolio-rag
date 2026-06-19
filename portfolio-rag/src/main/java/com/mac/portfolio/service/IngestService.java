package com.mac.portfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IngestService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    @Value("${portfolio.knowledge-path}")
    private String knowledgePath;

    public IngestService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("已清空 vector_store，开始重新入库");
        jdbcTemplate.execute("DELETE FROM vector_store");

        log.info("开始扫描知识库，路径：{}", knowledgePath);

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(knowledgePath);

        if (resources.length == 0) {
            log.warn("knowledge 目录下没有找到任何文件，请检查路径：{}", knowledgePath);
            return;
        }

        TokenTextSplitter splitter = new TokenTextSplitter();
        int total = 0;

        for (Resource resource : resources) {
            if (!resource.isReadable()) continue;
            try {
                List<Document> docs = new TikaDocumentReader(resource).get();
                List<Document> chunks = splitter.apply(docs);
                vectorStore.add(chunks);
                total++;
                log.info("已入库：{}，chunk 数量：{}", resource.getFilename(), chunks.size());
            } catch (Exception e) {
                log.error("文件入库失败：{}，原因：{}", resource.getFilename(), e.getMessage());
            }
        }

        log.info("知识库入库完成，共处理 {} 个文件", total);
    }
}
