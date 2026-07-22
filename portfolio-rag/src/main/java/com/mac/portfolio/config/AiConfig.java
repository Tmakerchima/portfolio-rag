package com.mac.portfolio.config;

import com.mac.portfolio.tool.PortfolioInfoTools;
import com.mac.portfolio.tool.AgentToolProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class AiConfig implements WebFluxConfigurer {

    @Value("classpath:prompts/interview-system.st")
    private Resource systemPromptResource;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) throws IOException {
        String systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);

        return builder.defaultSystem(systemPrompt).build();
    }

    @Bean
    public AgentToolProvider agentToolProvider(PortfolioInfoTools portfolioInfoTools,
                                               ToolCallbackProvider mcpTools) {
        return new AgentToolProvider(ToolCallbacks.from(portfolioInfoTools), mcpTools);
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
