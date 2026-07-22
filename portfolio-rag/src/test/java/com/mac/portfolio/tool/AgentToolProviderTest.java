package com.mac.portfolio.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolProviderTest {

    @Test
    void exposesGithubAndWeatherMcpToolsToTheLlmWithoutPreselectingOne() {
        ToolCallback github = tool("search_repositories");
        ToolCallback weather = tool("weather_mcp_get_forecast");
        ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[]{github, weather});

        AgentToolProvider tools = new AgentToolProvider(new ToolCallback[0], provider);

        assertThat(tools.mcpToolCount()).isEqualTo(2);
        assertThat(tools.toolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("search_repositories", "weather_mcp_get_forecast");
    }

    @Test
    void retriesMcpDiscoveryAfterAnInitialConnectionFailure() {
        ToolCallback github = tool("search_repositories");
        ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
        when(provider.getToolCallbacks())
                .thenThrow(new IllegalStateException("offline"))
                .thenReturn(new ToolCallback[]{github});

        AgentToolProvider tools = new AgentToolProvider(new ToolCallback[0], provider);

        assertThat(tools.toolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("search_repositories");
        assertThat(tools.mcpToolCount()).isEqualTo(1);
    }

    private ToolCallback tool(String name) {
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(definition);
        return callback;
    }
}
