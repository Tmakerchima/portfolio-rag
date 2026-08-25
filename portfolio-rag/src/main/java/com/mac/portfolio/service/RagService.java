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

    private static final String TOOLS_MARKER = "@@TOOLS@@";
    private static final String RETRIEVAL_USER_PROMPT = """
            请回答下面的问题。

            回答约束：
            - 只回答问题直接涉及的内容，不做完整简历复述，也不要扩展到未询问的经历。
            - 默认使用 2～4 句话；需要列举时最多 3 点。只有用户明确要求“详细”或“全部”时才适当展开。
            - 静态事实与趋势判断只能来自下面的检索上下文；如果上下文不足，直接说明资料中没有，不要推测。
            - github-trend.md 是带日期的趋势快照。使用其中内容时说明快照日期；用户要求实时 star、Issue、PR 或最新提交时仍按系统规则调用工具。
            - GitHub、博客等实时信息按系统规则调用工具；检索上下文只提供可追溯的静态背景和带日期快照。

            <retrieved_context>
            %s
            </retrieved_context>

            用户问题：%s
            """;

    private final ChatClient chatClient;
    private final HybridRetrievalService retrievalService;
    private final AgentToolProvider agentToolProvider;
    private final ObjectMapper objectMapper;

    public RagService(ChatClient chatClient, HybridRetrievalService retrievalService,
                      KnowledgeCorpusLoader knowledgeCorpusLoader,
                      AgentToolProvider agentToolProvider,
                      ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.retrievalService = retrievalService;
        this.agentToolProvider = agentToolProvider;
        this.objectMapper = objectMapper;
    }

    public Flux<String> streamAnswer(String question) {
        return Flux.defer(() -> {
            // 本轮请求专属的容器：哪些工具被实际调用，由 ToolUsageTrackingCallback 在执行时写入
            List<String> invokedTools = new CopyOnWriteArrayList<>();
            List<Document> retrieved = retrievalService.retrieve(question);
            String groundedPrompt = RETRIEVAL_USER_PROMPT.formatted(formatContext(retrieved), question);

            ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                    .user(groundedPrompt)
                    .toolContext(Map.of(ToolUsageTrackingCallback.CONTEXT_KEY, invokedTools));
            List<org.springframework.ai.tool.ToolCallback> availableTools = agentToolProvider.toolCallbacks();
            if (!availableTools.isEmpty()) {
                request.toolCallbacks(availableTools);
            }

            Flux<String> answer = request.stream().content();

            Mono<String> toolsFrame = Mono.fromSupplier(() -> toToolsFrame(invokedTools));
            return Flux.concat(answer, toolsFrame);
        });
    }

    private String formatContext(List<Document> documents) {
        if (documents.isEmpty()) return "未检索到与问题相关的静态资料。";

        StringBuilder context = new StringBuilder();
        for (Document document : documents) {
            context.append("<document source=\"")
                    .append(document.getMetadata().getOrDefault("source", "unknown"))
                    .append("\" section=\"")
                    .append(document.getMetadata().getOrDefault("section", "unknown"))
                    .append("\">\n")
                    .append(document.getText())
                    .append("\n</document>\n");
        }
        return context.toString().trim();
    }

    private String toToolsFrame(List<String> invokedTools) {
        try {
            return TOOLS_MARKER + objectMapper.writeValueAsString(invokedTools);
        } catch (Exception e) {
            return TOOLS_MARKER + "[]";
        }
    }

}
