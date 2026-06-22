package com.mac.portfolio.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;

// 包装一层 ToolCallback：执行时把"被调用的工具"记录进当前请求的 ToolContext，
// 供 RagService 在回答结束后告诉前端这次回答有没有用到 Function Calling / MCP
public class ToolUsageTrackingCallback implements ToolCallback {

    public static final String CONTEXT_KEY = "invokedTools";

    private final ToolCallback delegate;
    private final String label;

    public ToolUsageTrackingCallback(ToolCallback delegate, String origin) {
        this.delegate = delegate;
        this.label = origin + ":" + delegate.getToolDefinition().name();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String call(String toolInput, ToolContext toolContext) {
        if (toolContext != null) {
            Object tracker = toolContext.getContext().get(CONTEXT_KEY);
            if (tracker instanceof List<?> list) {
                ((List<String>) list).add(label);
            }
        }
        return delegate.call(toolInput, toolContext);
    }
}
