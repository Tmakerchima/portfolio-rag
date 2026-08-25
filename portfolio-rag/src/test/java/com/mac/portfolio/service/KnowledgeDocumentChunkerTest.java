package com.mac.portfolio.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeDocumentChunkerTest {

    @Test
    void actualProfileContainsOneFocusedLocalAgentChunk() throws Exception {
        KnowledgeDocumentChunker chunker = new KnowledgeDocumentChunker(1800);

        List<Document> chunks = chunker.chunk(new ClassPathResource("knowledge/about-mac.md"));

        assertThat(chunks)
                .filteredOn(document -> String.valueOf(document.getMetadata().get("project")).contains("LocalAgent"))
                .singleElement()
                .satisfies(document -> {
                    assertThat(document.getMetadata()).containsEntry("category", "projects");
                    assertThat(document.getText()).contains("Ollama", "Qwen 3.5 9B", "安全边界");
                });
    }

    @Test
    void splitsMarkdownBySemanticHeadingsAndAddsProjectMetadata() {
        KnowledgeDocumentChunker chunker = new KnowledgeDocumentChunker(1800);
        String markdown = """
                # 马驰

                ## 教育背景
                浙江工业大学软件工程。

                ## 项目经历

                ### 10. LocalAgent 本地编码 Agent
                使用 Ollama 和 Qwen 3.5 9B 构建本地编码 Agent。
                """;

        List<Document> chunks = chunker.splitMarkdown("about-mac.md", markdown);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("category", "education")
                .containsEntry("section", "教育背景");
        assertThat(chunks.get(1).getMetadata())
                .containsEntry("category", "projects")
                .containsEntry("project", "10. LocalAgent 本地编码 Agent")
                .containsEntry("topic", "10. LocalAgent 本地编码 Agent");
        assertThat(chunks.get(1).getText()).contains("Ollama", "Qwen 3.5 9B");
    }

    @Test
    void splitsOversizedSectionsWithoutExceedingConfiguredLimit() {
        KnowledgeDocumentChunker chunker = new KnowledgeDocumentChunker(80);
        String markdown = "## 项目经历\n\n### Long Project\n" + "Agent工具调用与安全边界。".repeat(20);

        List<Document> chunks = chunker.splitMarkdown("about-mac.md", markdown);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(document -> document.getText().length() <= 80);
    }

    @Test
    void classifiesGithubTrendSectionsForTrendRetrieval() {
        KnowledgeDocumentChunker chunker = new KnowledgeDocumentChunker(1800);
        String markdown = """
                # GitHub Trend 追踪

                ## GitHub 热门仓库观察

                ### volcengine/OpenViking
                Agent Context Database。
                """;

        List<Document> chunks = chunker.splitMarkdown("github-trend.md", markdown);

        assertThat(chunks).singleElement().satisfies(document -> {
            assertThat(document.getMetadata())
                    .containsEntry("source", "github-trend.md")
                    .containsEntry("category", "trends")
                    .containsEntry("topic", "volcengine/OpenViking");
            assertThat(document.getText()).contains("Agent Context Database");
        });
    }
}
