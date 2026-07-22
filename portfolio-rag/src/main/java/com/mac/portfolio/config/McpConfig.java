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
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + githubMcpPat)
                // 只暴露个人网站会使用的只读工具，降低工具描述占用并提高 LLM 自主选工具的准确率。
                .defaultHeader("X-MCP-Tools",
                        "get_me,search_repositories,search_issues,search_pull_requests," +
                                "get_file_contents,list_commits,get_latest_release,list_releases")
                .defaultHeader("X-MCP-Readonly", "true");
    }
}
