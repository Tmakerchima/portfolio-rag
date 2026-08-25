package com.mac.portfolio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PortfolioRetrievalEvaluationTest {

    @Test
    void curatedQuestionsKeepExpectedKnowledgeInTheRetrievedContext() throws Exception {
        KnowledgeDocumentChunker chunker = new KnowledgeDocumentChunker(1800);
        List<Document> corpus = new ArrayList<>();
        corpus.addAll(chunker.chunk(new ClassPathResource("knowledge/about-mac.md")));
        corpus.addAll(chunker.chunk(new ClassPathResource("knowledge/github-trend.md")));
        KnowledgeChunkStore chunkStore = new KnowledgeChunkStore();
        chunkStore.replace(corpus);
        HybridRetrievalService retrieval = new HybridRetrievalService(
                mock(VectorStore.class), chunkStore, 12, 4, 0.25, 4800, 0, false);

        ObjectMapper objectMapper = new ObjectMapper();
        List<EvaluationCase> cases = objectMapper.readValue(
                new ClassPathResource("portfolio-retrieval-cases.json").getInputStream(),
                new TypeReference<List<EvaluationCase>>() {});

        double reciprocalRank = 0.0;
        List<String> failures = new ArrayList<>();
        for (EvaluationCase evaluationCase : cases) {
            List<Document> results = retrieval.retrieve(evaluationCase.question());
            int rank = matchingRank(results, evaluationCase);
            if (rank == 0) {
                failures.add("'%s' -> %s".formatted(evaluationCase.question(), describe(results)));
            } else {
                reciprocalRank += 1.0 / rank;
            }
        }

        double meanReciprocalRank = reciprocalRank / cases.size();
        assertThat(cases).hasSizeBetween(20, 30);
        assertThat(failures).withFailMessage("Missing expected retrieval hits: %s", failures).isEmpty();
        assertThat(meanReciprocalRank).isGreaterThanOrEqualTo(0.70);
    }

    private int matchingRank(List<Document> results, EvaluationCase expected) {
        String expectedText = expected.text().toLowerCase(Locale.ROOT);
        for (int index = 0; index < results.size(); index++) {
            Document document = results.get(index);
            if (expected.source().equals(document.getMetadata().get("source"))
                    && expected.category().equals(document.getMetadata().get("category"))
                    && document.getText().toLowerCase(Locale.ROOT).contains(expectedText)) {
                return index + 1;
            }
        }
        return 0;
    }

    private String describe(List<Document> documents) {
        return documents.stream()
                .map(document -> document.getMetadata().get("source") + "/"
                        + document.getMetadata().get("topic"))
                .toList()
                .toString();
    }

    private record EvaluationCase(String question, String source, String category, String text) {}
}
