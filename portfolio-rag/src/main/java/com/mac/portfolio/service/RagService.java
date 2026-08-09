package com.mac.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.portfolio.tool.AgentToolProvider;
import com.mac.portfolio.tool.ToolUsageTrackingCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RagService {

    // 简历问答直接传入完整 about-mac.md；SSE 只需要标记实际调用的 Function Calling / MCP 工具。
    private static final String TOOLS_MARKER = "@@TOOLS@@";
    private static final String FULL_CONTEXT_USER_PROMPT = """
            请回答下面的问题。

            回答约束：
            - 只回答问题直接涉及的内容，不做完整简历复述，也不要扩展到未询问的经历。
            - 默认使用 2～4 句话；需要列举时最多 3 点。只有用户明确要求“详细”或“全部”时才适当展开。
            - 静态简历事实只能来自下面的完整简历上下文；如果上下文不足，直接说明资料中没有，不要推测。
            - GitHub、博客等实时信息仍按系统规则调用工具；完整简历上下文只提供静态背景。

            <resume_context>
            %s
            </resume_context>

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
            // 本轮请求专属的容器：哪些工具被实际调用，由 ToolUsageTrackingCallback 在执行时写入
            List<String> invokedTools = new CopyOnWriteArrayList<>();
            String groundedPrompt = FULL_CONTEXT_USER_PROMPT.formatted(resumeContextProvider.content(), question);

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

    private String toToolsFrame(List<String> invokedTools) {
        try {
            return TOOLS_MARKER + objectMapper.writeValueAsString(invokedTools);
        } catch (Exception e) {
            return TOOLS_MARKER + "[]";
        }
    }

}
