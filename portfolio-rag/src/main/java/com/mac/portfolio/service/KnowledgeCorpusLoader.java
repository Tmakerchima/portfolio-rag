package com.mac.portfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Loads every configured portfolio knowledge document into the in-memory retrieval corpus. */
@Component
public class KnowledgeCorpusLoader {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeCorpusLoader.class);

    private final List<Resource> resources;
    private final List<Document> chunks;

    public KnowledgeCorpusLoader(
            ResourcePatternResolver resourceResolver,
            KnowledgeDocumentChunker documentChunker,
            KnowledgeChunkStore chunkStore,
            @Value("${portfolio.knowledge-path:classpath*:knowledge/**/*}") String knowledgePath) {
        try {
            this.resources = Arrays.stream(resourceResolver.getResources(knowledgePath))
                    .filter(Resource::exists)
                    .filter(Resource::isReadable)
                    .filter(resource -> resource.getFilename() != null)
                    .sorted(Comparator.comparing(Resource::getFilename))
                    .toList();
            if (resources.isEmpty()) {
                throw new IllegalStateException("No portfolio knowledge documents found at " + knowledgePath);
            }

            List<Document> loadedChunks = new ArrayList<>();
            for (Resource resource : resources) {
                loadedChunks.addAll(documentChunker.chunk(resource));
            }
            if (loadedChunks.isEmpty()) {
                throw new IllegalStateException("Portfolio knowledge documents produced no retrievable chunks");
            }

            this.chunks = List.copyOf(loadedChunks);
            chunkStore.replace(chunks);
            log.info("已加载 Portfolio 知识文档 {} 份，共 {} 个检索片段：{}",
                    resources.size(), chunks.size(),
                    resources.stream().map(Resource::getFilename).toList());
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load portfolio knowledge corpus", error);
        }
    }

    public List<String> sources() {
        return resources.stream().map(Resource::getFilename).toList();
    }

    public int chunkCount() {
        return chunks.size();
    }
}
