package com.mac.portfolio.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeCorpusLoaderTest {

    @Test
    void loadsProfileAndGithubTrendIntoRetrievalCorpus() {
        KnowledgeChunkStore chunkStore = new KnowledgeChunkStore();
        KnowledgeCorpusLoader loader = new KnowledgeCorpusLoader(
                new PathMatchingResourcePatternResolver(),
                new KnowledgeDocumentChunker(1800),
                chunkStore,
                "classpath*:knowledge/**/*");

        assertThat(loader.sources()).contains("about-mac.md", "github-trend.md");
        assertThat(loader.chunkCount()).isEqualTo(chunkStore.snapshot().size());
        assertThat(chunkStore.snapshot())
                .anySatisfy(document -> assertThat(document.getMetadata())
                        .containsEntry("source", "github-trend.md")
                        .containsEntry("category", "trends"));
    }
}
