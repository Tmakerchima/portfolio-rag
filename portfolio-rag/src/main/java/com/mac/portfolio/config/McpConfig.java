package com.mac.portfolio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

// MCP Client 的 Streamable HTTP 传输基于这个 WebClient.Builder 派生，
// 给 GitHub 远程 MCP Server 的每次请求都带上 PAT 认证
@Configuration
public class McpConfig {

    @Value("${github.mcp-pat}")
    private String githubMcpPat;

    @Bean
    public WebClient.Builder mcpWebClientBuilder() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + githubMcpPat);
    }
}
