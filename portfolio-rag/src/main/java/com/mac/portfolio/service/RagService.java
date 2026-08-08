package com.mac.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.portfolio.tool.AgentToolProvider;
import com.mac.portfolio.tool.ToolUsageTrackingCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
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
    private static final String GROUNDED_USER_PROMPT = """
            请回答下面的问题。

            回答约束：
            - 只回答问题直接涉及的内容，不做完整简历复述，也不要扩展到未询问的经历。
            - 默认使用 2～4 句话；需要列举时最多 3 点。只有用户明确要求“详细”或“全部”时才适当展开。
            - 静态简历事实只能来自检索上下文；如果上下文不足，直接说明未检索到，不要用相邻片段补齐。
            - GitHub、博客等实时信息仍按系统规则调用工具，检索上下文只提供静态背景。

            <retrieved_context>
            %s
            </retrieved_context>

            用户问题：%s
            """;

    private final ChatClient chatClient;
    private final ResumeContextProvider resumeContextProvider;
    private final AgentToolProvider agentToolProvider;
    private final ObjectMapper objectMapper;

    public RagService(ChatClient chatClient, ResumeContextProvider resumeContextProvider,
                      AgentToolProvider agentToolProvider,
                      ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.resumeContextProvider = resumeContextProvider;
        this.agentToolProvider = agentToolProvider;
        this.objectMapper = objectMapper;
    }

    public Flux<String> streamAnswer(String question) {
        return Flux.defer(() -> {
            // about-mac.md is intentionally small and authoritative for static resume facts.
            // Pass the complete file; do not query or mutate the legacy vector_store.
            Document document = Document.builder()
                    .id("resume-about-mac")
                    .text(resumeContextProvider.content())
                    .metadata(Map.of("source", "about-mac.md", "context_mode", "full"))
                    .score(1.0)
                    .build();
            List<Document> documents = List.of(document);
            String sourcesFrame = toSourcesFrame(documents);

            // 本轮请求专属的容器：哪些工具被实际调用，由 ToolUsageTrackingCallback 在执行时写入
            List<String> invokedTools = new CopyOnWriteArrayList<>();
            String groundedPrompt = GROUNDED_USER_PROMPT.formatted(formatContext(documents), question);

            ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                    .user(groundedPrompt)
                    .toolContext(Map.of(ToolUsageTrackingCallback.CONTEXT_KEY, invokedTools));
            List<org.springframework.ai.tool.ToolCallback> availableTools = agentToolProvider.toolCallbacks();
            if (!availableTools.isEmpty()) {
                request.toolCallbacks(availableTools);
            }

            Flux<String> answer = request.stream().content();

            Mono<String> toolsFrame = Mono.fromSupplier(() -> toToolsFrame(invokedTools));
            return Flux.concat(Mono.just(sourcesFrame), answer, toolsFrame);
        });
    }

    private String formatContext(List<Document> documents) {
        if (documents.isEmpty()) return "（没有检索到足够相关的静态简历片段）";
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            context.append("[片段 ").append(i + 1).append(" | ")
                    .append(document.getMetadata().getOrDefault("section", "未分类"));
            Object topic = document.getMetadata().get("topic");
            if (topic != null) context.append(" / ").append(topic);
            context.append("]\n").append(document.getText()).append("\n\n");
        }
        return context.toString().trim();
    }

    private String toSourcesFrame(List<Document> documents) {
        List<Map<String, Object>> sources = documents.stream()
                .map(doc -> Map.<String, Object>of(
                        "source", doc.getMetadata().getOrDefault("source", "unknown"),
                        "section", doc.getMetadata().getOrDefault("section", "未分类"),
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
