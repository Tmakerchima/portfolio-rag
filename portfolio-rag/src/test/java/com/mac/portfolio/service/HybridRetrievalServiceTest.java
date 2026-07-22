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
                vectorStore, chunkStore, 10, 2, 0.35, 2800);

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
                vectorStore, chunkStore, 10, 3, 0.35, 2800);

        List<Document> result = service.retrieve("LocalAgent 的 Ollama 模型");

        assertThat(result).singleElement().satisfies(document ->
                assertThat(document.getText()).contains("Ollama"));
    }

    private Document document(String id, String text, String topic, Double score) {
        return Document.builder()
                .id(id)
                .text(text)
                .metadata(Map.of(
                        "source", "about-mac.md",
                        "category", "projects",
                        "section", "项目经历",
                        "topic", topic,
                        "project", topic,
                        "chunk_id", id))
                .score(score)
                .build();
    }
}
