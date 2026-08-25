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
import java.util.Arrays;

@Configuration
public class AiConfig implements WebFluxConfigurer {

    private final String[] allowedOrigins;

    public AiConfig(@Value("${portfolio.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank() && !"*".equals(origin))
                .distinct()
                .toArray(String[]::new);
        if (this.allowedOrigins.length == 0) {
            throw new IllegalArgumentException("portfolio.cors.allowed-origins must contain at least one explicit origin");
        }
    }

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

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }

    String[] allowedOrigins() {
        return allowedOrigins.clone();
    }
}
