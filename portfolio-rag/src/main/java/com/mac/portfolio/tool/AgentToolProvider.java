package com.mac.portfolio.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 向每次 ChatClient 请求提供 Agent 工具。LLM 仍然自主决定是否调用；
 * 这里只负责发现、跟踪和在 MCP 首次连接失败后重试。
 */
public class AgentToolProvider {

    private static final Logger log = LoggerFactory.getLogger(AgentToolProvider.class);

    private final List<ToolCallback> localTools;
    private final ToolCallbackProvider mcpProvider;
    private volatile List<ToolCallback> mcpTools = List.of();

    public AgentToolProvider(ToolCallback[] localTools, ToolCallbackProvider mcpProvider) {
        this.localTools = Arrays.stream(localTools)
                .map(callback -> (ToolCallback) new ToolUsageTrackingCallback(callback, "function-calling"))
                .toList();
        this.mcpProvider = mcpProvider;
        refreshMcpTools();
    }

    public List<ToolCallback> toolCallbacks() {
        if (mcpTools.isEmpty()) {
            refreshMcpTools();
        }
        List<ToolCallback> allTools = new ArrayList<>(localTools.size() + mcpTools.size());
        allTools.addAll(localTools);
        allTools.addAll(mcpTools);
        return List.copyOf(allTools);
    }

    public int mcpToolCount() {
        return mcpTools.size();
    }

    private synchronized void refreshMcpTools() {
        if (!mcpTools.isEmpty()) return;
        try {
            mcpTools = Arrays.stream(mcpProvider.getToolCallbacks())
                    .map(callback -> (ToolCallback) new ToolUsageTrackingCallback(callback, "mcp"))
                    .toList();
            log.info("已注册 {} 个 MCP 工具：{}", mcpTools.size(),
                    mcpTools.stream().map(callback -> callback.getToolDefinition().name()).toList());
        } catch (Exception e) {
            log.warn("MCP 工具发现失败；本次请求不提供 MCP，后续请求将重试：{}", e.getMessage());
            mcpTools = List.of();
        }
    }
}
