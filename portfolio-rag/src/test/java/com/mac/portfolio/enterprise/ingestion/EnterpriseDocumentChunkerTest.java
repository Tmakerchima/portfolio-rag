package com.mac.portfolio.enterprise.ingestion;

import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnterpriseDocumentChunkerTest {

    @Test
    void preservesParagraphBoundariesAndCreatesStableChunkIds() {
        EnterpriseDocumentChunker chunker = new EnterpriseDocumentChunker(200);
        EnterpriseDocumentInput input = new EnterpriseDocumentInput(
                "dsid_123_report.txt", "enterprise-rag-bench", "github", "Upload limits",
                "Title\n\nThe file limit is 10 MiB.\n\nThe request limit is 50 MiB.",
                "default", "engineering", "public", Map.of(), null);

        var first = chunker.chunk(input);
        var second = chunker.chunk(input);

        assertThat(first).hasSize(1);
        assertThat(first).containsExactlyElementsOf(second);
        assertThat(first.getFirst().content()).contains("10 MiB", "50 MiB");
    }

    @Test
    void splitsLongParagraphsWithoutReturningBlankChunks() {
        EnterpriseDocumentChunker chunker = new EnterpriseDocumentChunker(200);
        String content = "x".repeat(450);
        EnterpriseDocumentInput input = new EnterpriseDocumentInput(
                "doc", "bench", "slack", "Long", content,
                "default", "engineering", "public", Map.of(), null);

        assertThat(chunker.chunk(input)).hasSize(3).allSatisfy(chunk ->
                assertThat(chunk.content()).isNotBlank().hasSizeLessThanOrEqualTo(200));
    }
}
