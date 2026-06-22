package com.mac.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.portfolio.tool.ToolUsageTrackingCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RagService {

    // 流式响应的 data 帧用这两个前缀区分：检索到的简历片段（RAG）、本轮实际调用的工具（Function Calling / MCP）
    private static final String SOURCES_MARKER = "@@SOURCES@@";
    private static final String TOOLS_MARKER = "@@TOOLS@@";

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public RagService(ChatClient chatClient, VectorStore vectorStore, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    public Flux<String> streamAnswer(String question) {
        // 检索最相关的 5 个简历片段，相似度阈值 0.5
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(5)
                .similarityThreshold(0.5)
                .build();

        Mono<String> sourcesFrame = Mono.fromCallable(() -> vectorStore.similaritySearch(searchRequest))
                .map(this::toSourcesFrame);

        // 本轮请求专属的容器：哪些工具被实际调用，由 ToolUsageTrackingCallback 在执行时写入
        List<String> invokedTools = new CopyOnWriteArrayList<>();

        Flux<String> answer = chatClient.prompt()
                .user(question)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(searchRequest)
                        .build())
                .toolContext(Map.of(ToolUsageTrackingCallback.CONTEXT_KEY, invokedTools))
                .stream()
                .content();

        Mono<String> toolsFrame = Mono.fromSupplier(() -> toToolsFrame(invokedTools));

        return Flux.concat(sourcesFrame, answer, toolsFrame);
    }

    private String toSourcesFrame(List<Document> documents) {
        List<Map<String, Object>> sources = documents.stream()
                .map(doc -> Map.<String, Object>of(
                        "source", doc.getMetadata().getOrDefault("source", "unknown"),
                        "snippet", snippet(doc.getText()),
                        "score", doc.getScore() == null ? 0.0 : doc.getScore()))
                .toList();
        try {
            return SOURCES_MARKER + objectMapper.writeValueAsString(sources);
        } catch (Exception e) {
            return SOURCES_MARKER + "[]";
        }
    }

    private String toToolsFrame(List<String> invokedTools) {
        try {
            return TOOLS_MARKER + objectMapper.writeValueAsString(invokedTools);
        } catch (Exception e) {
            return TOOLS_MARKER + "[]";
        }
    }

    // SSE 每个 data 帧必须单行，把 text 里的换行压成空格，并截断到合理长度
    private String snippet(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 120 ? oneLine.substring(0, 120) + "…" : oneLine;
    }
}
