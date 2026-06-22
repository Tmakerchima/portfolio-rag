package com.mac.portfolio.config;

import com.mac.portfolio.tool.PortfolioInfoTools;
import com.mac.portfolio.tool.ToolUsageTrackingCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class AiConfig implements WebFluxConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Value("classpath:prompts/interview-system.st")
    private Resource systemPromptResource;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, PortfolioInfoTools portfolioInfoTools,
                                  ToolCallbackProvider mcpTools) throws IOException {
        String systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);

        // 每个工具都包一层跟踪，这样 RagService 才能知道一次回答到底有没有触发 Function Calling / MCP
        List<ToolCallback> trackedCallbacks = new ArrayList<>();
        for (ToolCallback callback : ToolCallbacks.from(portfolioInfoTools)) {
            trackedCallbacks.add(new ToolUsageTrackingCallback(callback, "function-calling"));
        }

        // GitHub 远程 MCP Server 偶发连接慢/超时，这里握手失败时不能让整个应用起不来——
        // 降级为没有 MCP 工具，RAG 和 Function Calling 照常工作
        try {
            for (ToolCallback callback : mcpTools.getToolCallbacks()) {
                trackedCallbacks.add(new ToolUsageTrackingCallback(callback, "mcp"));
            }
        } catch (Exception e) {
            log.warn("GitHub MCP Server 连接失败，本次启动跳过 MCP 工具：{}", e.getMessage());
        }

        return builder.defaultSystem(systemPrompt)
                .defaultToolCallbacks(trackedCallbacks)
                .build();
    }

    // 开发期允许所有来源跨域，上线后改为前端域名
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
