package com.mac.portfolio.service;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KnowledgeSourceService {

    private final Clock clock;

    public KnowledgeSourceService() {
        this(Clock.system(ZoneId.of("Asia/Taipei")));
    }

    KnowledgeSourceService(Clock clock) {
        this.clock = clock;
    }

    public List<KnowledgeSource> summarize(List<Document> documents) {
        Map<String, KnowledgeSource> unique = new LinkedHashMap<>();
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            String source = value(metadata, "source");
            String section = value(metadata, "section");
            String snapshotDate = value(metadata, "snapshot_date");
            String expiresAt = value(metadata, "expires_at");
            KnowledgeSource item = new KnowledgeSource(
                    source.isBlank() ? "unknown" : source,
                    section,
                    snapshotDate,
                    expiresAt,
                    isStale(expiresAt));
            unique.putIfAbsent(item.source() + "\u0000" + item.section(), item);
        }
        return List.copyOf(unique.values());
    }

    public String freshnessNote(Document document) {
        String snapshotDate = value(document.getMetadata(), "snapshot_date");
        String expiresAt = value(document.getMetadata(), "expires_at");
        if (snapshotDate.isBlank() && expiresAt.isBlank()) return "";
        if (isStale(expiresAt)) {
            return "快照日期 " + snapshotDate + "，已于 " + expiresAt + " 过期；不得作为当前事实，实时问题必须调用工具或明确说明无法核验。";
        }
        return "快照日期 " + snapshotDate + "，有效期至 " + expiresAt + "。";
    }

    private boolean isStale(String expiresAt) {
        if (expiresAt.isBlank()) return false;
        try {
            return LocalDate.now(clock).isAfter(LocalDate.parse(expiresAt));
        } catch (DateTimeParseException ignored) {
            return true;
        }
    }

    private static String value(Map<String, Object> metadata, String key) {
        return String.valueOf(metadata.getOrDefault(key, "")).trim();
    }

    public record KnowledgeSource(
            String source,
            String section,
            String snapshotDate,
            String expiresAt,
            boolean stale) {
    }
}
