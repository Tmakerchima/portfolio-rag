package com.mac.portfolio.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridRetrievalServiceTest {

    @Test
    void combinesMetadataVectorAndLexicalSignalsForSpecificProjectQuestion() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkStore chunkStore = new KnowledgeChunkStore();
        Document fundLens = document("fund", "FundLens 量化趋势研究助手", "9. FundLens", 0.86);
        Document localAgent = document("local", "LocalAgent 使用 Ollama Qwen 3.5 9B 和工具调用循环", "10. LocalAgent", 0.78);
        chunkStore.replace(List.of(fundLens, localAgent));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(fundLens, localAgent));

        HybridRetrievalService service = new HybridRetrievalService(
                vectorStore, chunkStore, 10, 2, 0.35, 2800, 0, true);

        List<Document> result = service.retrieve("LocalAgent 项目用了什么本地模型？");

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getMetadata()).containsEntry("topic", "10. LocalAgent");
        assertThat(result.get(0).getText()).contains("Ollama", "Qwen 3.5 9B");

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().hasFilterExpression()).isTrue();
    }

    @Test
    void lexicalSearchStillWorksWhenVectorStoreFails() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkStore chunkStore = new KnowledgeChunkStore();
        Document localAgent = document("local", "LocalAgent 使用 Ollama 本地推理", "10. LocalAgent", null);
        chunkStore.replace(List.of(localAgent));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new IllegalStateException("offline"));

        HybridRetrievalService service = new HybridRetrievalService(
                vectorStore, chunkStore, 10, 3, 0.35, 2800, 0, true);

        List<Document> result = service.retrieve("LocalAgent 的 Ollama 模型");

        assertThat(result).singleElement().satisfies(document ->
                assertThat(document.getText()).contains("Ollama"));
    }

    @Test
    void intentMatchedEducationChunkWinsEvenWhenVectorScoresAreFlat() {
        // 模拟真实线上现象：短问题的向量分挤在 0.3 附近，教育片段被阈值过滤掉（未出现在向量召回中），
        // 其他无关片段反而有更高的向量分。意图命中加权必须让教育片段跳到第一。
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkStore chunkStore = new KnowledgeChunkStore();
        Document education = document("edu", "浙江工业大学软件工程专业，2017-2021 就读", "教育背景", null,
                Map.of("category", "education", "section", "教育背景", "topic", "教育背景"));
        Document skills = document("skills", "熟悉 Java Spring Boot Hadoop", "技术栈与能力", 0.30,
                Map.of("category", "skills", "section", "技术栈与能力", "topic", "技术栈与能力"));
        Document career = document("career", "杭州云融 Java 后端工程师", "职业生涯", 0.30,
                Map.of("category", "career", "section", "职业生涯", "topic", "职业生涯"));
        chunkStore.replace(List.of(education, skills, career));
        // 向量召回里只有 skills / career（教育片段因相似度低于阈值没进候选）
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(skills, career));

        HybridRetrievalService service = new HybridRetrievalService(
                vectorStore, chunkStore, 10, 3, 0.35, 2800, 0, true);

        List<Document> result = service.retrieve("马驰毕业于哪个学校");

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getMetadata()).containsEntry("topic", "教育背景");
        assertThat(result.get(0).getText()).contains("浙江工业大学");
    }

    @Test
    void smallCorpusReturnsFullContextInDocumentOrder() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkStore chunkStore = new KnowledgeChunkStore();
        Document first = document("a", "基本信息片段", "基本信息", 0.5,
                Map.of("category", "basic", "section", "基本信息", "topic", "基本信息", "chunk_index", 0));
        Document second = document("b", "教育背景片段", "教育背景", 0.5,
                Map.of("category", "education", "section", "教育背景", "topic", "教育背景", "chunk_index", 1));
        chunkStore.replace(List.of(first, second));

        HybridRetrievalService service = new HybridRetrievalService(
                vectorStore, chunkStore, 10, 3, 0.35, 2800, 8000, true);

        List<Document> result = service.retrieve("马驰毕业于哪个学校");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMetadata()).containsEntry("topic", "基本信息");
        assertThat(result.get(1).getMetadata()).containsEntry("topic", "教育背景");
        // 全量模式下每个片段都应带满分散入上下文，且不做 top-k 截断
        assertThat(result).allSatisfy(document -> assertThat(document.getScore()).isEqualTo(1.0));
    }

    @Test
    void largeCorpusStillUsesHybridRetrieval() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkStore chunkStore = new KnowledgeChunkStore();
        Document doc = document("a", "x".repeat(9000), "大型语料片段", 0.5,
                Map.of("category", "general", "section", "大型语料", "topic", "大型语料", "chunk_index", 0));
        chunkStore.replace(List.of(doc));

        HybridRetrievalService service = new HybridRetrievalService(
                vectorStore, chunkStore, 10, 3, 0.35, 2800, 8000, true);

        List<Document> result = service.retrieve("随便问一句");
        // 语料超过 full-context-max-chars，不应走全量；向量检索被 mock 返回空 → 最终无候选
        assertThat(result).isEmpty();
    }

    @Test
    void githubTrendQuestionPrefersDatedTrendKnowledge() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkStore chunkStore = new KnowledgeChunkStore();
        Document profile = document("profile", "马驰的 GitHub 与 Java 项目", "基本信息", 0.82,
                Map.of("category", "basic", "section", "基本信息", "topic", "基本信息"));
        Document trend = document("trend", "GitHub 热门 Agent 趋势包括 OpenViking 上下文数据库与 Maka 审计运行时",
                "GitHub 热门仓库观察", null,
                Map.of("source", "github-trend.md", "category", "trends",
                        "section", "GitHub 热门仓库观察", "topic", "GitHub 热门仓库观察"));
        chunkStore.replace(List.of(profile, trend));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(profile));

        HybridRetrievalService service = new HybridRetrievalService(
                vectorStore, chunkStore, 10, 3, 0.35, 2800, 0, false);

        List<Document> result = service.retrieve("最近 GitHub 上有哪些 Agent 趋势值得关注？");

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getMetadata())
                .containsEntry("source", "github-trend.md")
                .containsEntry("category", "trends");
        assertThat(result.get(0).getText()).contains("OpenViking", "Maka");
    }

    @Test
    void personalGithubProjectQuestionDoesNotGetMisroutedToTrendSnapshot() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkStore chunkStore = new KnowledgeChunkStore();
        Document project = document("project", "马驰最近完成 Life Adventure 和 EnterpriseRAG 项目",
                "代表性 GitHub 项目", null,
                Map.of("category", "projects", "section", "代表性 GitHub 项目", "topic", "代表性 GitHub 项目"));
        Document trend = document("trend", "GitHub 热门 Agent 趋势包括 OpenViking",
                "GitHub 热门仓库观察", null,
                Map.of("source", "github-trend.md", "category", "trends",
                        "section", "GitHub 热门仓库观察", "topic", "GitHub 热门仓库观察"));
        chunkStore.replace(List.of(project, trend));

        HybridRetrievalService service = new HybridRetrievalService(
                vectorStore, chunkStore, 10, 3, 0.35, 2800, 0, false);

        List<Document> result = service.retrieve("马驰最近完成了哪些 GitHub 项目？");

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getMetadata()).containsEntry("category", "projects");
        assertThat(result.get(0).getText()).contains("Life Adventure", "EnterpriseRAG");
    }

    @Test
    void bm25RewardsRareTermsInsteadOfSimpleTokenOverlap() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkStore chunkStore = new KnowledgeChunkStore();
        Document rare = document("rare", "Spring LocalAgent 本地执行", "LocalAgent", null);
        Document commonOne = document("common-1", "Spring 后端服务", "服务一", null);
        Document commonTwo = document("common-2", "Spring 数据接口", "服务二", null);
        List<Document> documents = List.of(rare, commonOne, commonTwo);
        HybridRetrievalService service = new HybridRetrievalService(
                vectorStore, chunkStore, 10, 3, 0.35, 2800, 0, false);

        Map<String, Double> scores = service.bm25Scores("Spring LocalAgent", documents);

        assertThat(scores.get("rare")).isGreaterThan(scores.get("common-1"));
    }

    @Test
    void comprehensiveProjectQuestionExpandsTopKBudget() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeChunkStore chunkStore = new KnowledgeChunkStore();
        List<Document> projects = java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> document("project-" + index, "项目 " + index, "项目 " + index, null))
                .toList();
        chunkStore.replace(projects);
        HybridRetrievalService service = new HybridRetrievalService(
                vectorStore, chunkStore, 10, 4, 0.35, 2800, 0, false);

        List<Document> result = service.retrieve("完整列出全部项目");

        assertThat(result).hasSize(8);
    }

    private Document document(String id, String text, String topic, Double score, Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new java.util.HashMap<>(Map.of(
                "source", "about-mac.md",
                "category", "projects",
                "section", "项目经历",
                "topic", topic,
                "project", topic,
                "chunk_id", id));
        metadata.putAll(extraMetadata);
        return Document.builder()
                .id(id)
                .text(text)
                .metadata(metadata)
                .score(score)
                .build();
    }

    private Document document(String id, String text, String topic, Double score) {
        return document(id, text, topic, score, Map.of());
    }
}
