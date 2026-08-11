package com.mac.portfolio.enterprise.ingestion;

import com.mac.portfolio.enterprise.model.EnterpriseChunk;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnterpriseChunkContextualizerTest {

    @Test
    void disabledModeIndexesOnlyOriginalEvidence() {
        ChatClient client = mock(ChatClient.class);
        EnterpriseChunkContextualizer contextualizer = new EnterpriseChunkContextualizer(
                client, new JTokkitTokenCountEstimator(), false, false, 10_000, 400);

        var indexed = contextualizer.contextualize(document(), chunk());

        assertThat(indexed.contextualPrefix()).isEmpty();
        assertThat(indexed.indexContent()).isEqualTo("Revenue increased by 3%.");
        verifyNoInteractions(client);
    }

    @Test
    void enabledModeKeepsGeneratedPrefixSeparateFromEvidence() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("This chunk is from ACME's Q2 2026 revenue report.");
        EnterpriseChunkContextualizer contextualizer = new EnterpriseChunkContextualizer(
                client, new JTokkitTokenCountEstimator(), true, false, 10_000, 400);

        var indexed = contextualizer.contextualize(document(), chunk());

        assertThat(indexed.chunk().content()).isEqualTo("Revenue increased by 3%.");
        assertThat(indexed.contextualPrefix()).contains("ACME", "Q2 2026");
        assertThat(indexed.indexContent()).startsWith(indexed.contextualPrefix()).endsWith(indexed.chunk().content());
    }

    private EnterpriseDocumentInput document() {
        return new EnterpriseDocumentInput("report-1", "drive", "pdf", "ACME Q2 2026",
                "ACME Q2 2026 results.\n\nRevenue increased by 3%.",
                "tenant", "finance", "confidential", Map.of(), null);
    }

    private EnterpriseChunk chunk() {
        return new EnterpriseChunk("chunk-1", 0, "Revenue increased by 3%.", "ACME Q2 2026 > Revenue", 7);
    }
}
