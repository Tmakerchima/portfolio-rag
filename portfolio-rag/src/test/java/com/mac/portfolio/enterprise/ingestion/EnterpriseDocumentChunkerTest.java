package com.mac.portfolio.enterprise.ingestion;

import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import org.junit.jupiter.api.Test;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class EnterpriseDocumentChunkerTest {

    @Test
    void preservesMarkdownSectionsAndCreatesStableChunkIds() {
        EnterpriseDocumentChunker chunker = new EnterpriseDocumentChunker(new WordTokenEstimator(), 40, 4);
        EnterpriseDocumentInput input = input("""
                # Upload API

                The file limit is 10 MiB and applies to every upload request.

                ## Request limits

                The request limit is 50 MiB for engineering tenants.
                """);

        var first = chunker.chunk(input);
        var second = chunker.chunk(input);

        assertThat(first).hasSize(2).containsExactlyElementsOf(second);
        assertThat(first).extracting(chunk -> chunk.sectionPath())
                .containsExactly("Title > Upload API", "Title > Upload API > Request limits");
        assertThat(first.getFirst().content()).contains("10 MiB");
        assertThat(first.getLast().content()).contains("50 MiB");
    }

    @Test
    void splitsOversizedBlocksByTokenBudget() {
        EnterpriseDocumentChunker chunker = new EnterpriseDocumentChunker(new WordTokenEstimator(), 32, 0);
        String content = IntStream.range(0, 90).mapToObj(index -> "word" + index)
                .reduce((left, right) -> left + " " + right).orElseThrow();

        var chunks = chunker.chunk(input(content));

        assertThat(chunks).hasSizeGreaterThan(1).allSatisfy(chunk -> {
            assertThat(chunk.content()).isNotBlank();
            assertThat(chunk.tokenCount()).isLessThanOrEqualTo(32);
        });
    }

    @Test
    void overlapsOnlyWhenConsecutiveChunksStayInTheSameSection() {
        EnterpriseDocumentChunker chunker = new EnterpriseDocumentChunker(new WordTokenEstimator(), 32, 5);
        String firstParagraph = IntStream.range(0, 24).mapToObj(index -> "first" + index)
                .reduce((left, right) -> left + " " + right).orElseThrow();
        String secondParagraph = IntStream.range(0, 24).mapToObj(index -> "second" + index)
                .reduce((left, right) -> left + " " + right).orElseThrow();

        var chunks = chunker.chunk(input("# Same section\n\n" + firstParagraph + "\n\n" + secondParagraph));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.getLast().content()).contains("first23", "second0");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.tokenCount()).isLessThanOrEqualTo(32));
    }

    private EnterpriseDocumentInput input(String content) {
        return new EnterpriseDocumentInput(
                "doc", "bench", "github", "Title", content,
                "default", "engineering", "public", Map.of(), null);
    }

    private static final class WordTokenEstimator implements TokenCountEstimator {
        @Override
        public int estimate(String text) {
            String normalized = text == null ? "" : text.trim();
            return normalized.isEmpty() ? 0 : normalized.split("\\s+").length;
        }

        @Override
        public int estimate(MediaContent content) {
            return estimate(content == null ? "" : content.toString());
        }

        @Override
        public int estimate(Iterable<MediaContent> contents) {
            int total = 0;
            if (contents != null) for (MediaContent content : contents) total += estimate(content);
            return total;
        }
    }
}
