package com.mac.portfolio.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSourceServiceTest {

    @Test
    void marksExpiredTrendSnapshotsAndDeduplicatesSources() {
        KnowledgeSourceService service = new KnowledgeSourceService(
                Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC));
        Document first = document("a", "2026-08-20");
        Document duplicate = document("b", "2026-08-20");

        List<KnowledgeSourceService.KnowledgeSource> sources = service.summarize(List.of(first, duplicate));

        assertThat(sources).singleElement().satisfies(source -> {
            assertThat(source.source()).isEqualTo("github-trend.md");
            assertThat(source.stale()).isTrue();
        });
        assertThat(service.freshnessNote(first)).contains("已于 2026-08-20 过期");
    }

    private Document document(String id, String expiresAt) {
        return Document.builder().id(id).text("trend").metadata(Map.of(
                "source", "github-trend.md",
                "section", "本周自动快照",
                "snapshot_date", "2026-08-01",
                "expires_at", expiresAt)).build();
    }
}
