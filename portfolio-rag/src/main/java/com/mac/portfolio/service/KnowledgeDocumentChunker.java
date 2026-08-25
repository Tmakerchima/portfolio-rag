package com.mac.portfolio.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KnowledgeDocumentChunker {

    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+?)\\s*$");
    private static final Pattern FRONT_MATTER = Pattern.compile("\\A---\\R(.*?)\\R---(?:\\R|\\z)", Pattern.DOTALL);
    private final int maxChunkChars;

    public KnowledgeDocumentChunker(
            @Value("${portfolio.rag.max-chunk-chars:1800}") int maxChunkChars) {
        this.maxChunkChars = maxChunkChars;
    }

    public List<Document> chunk(Resource resource) throws IOException {
        String source = resource.getFilename() == null ? resource.getDescription() : resource.getFilename();
        if (source.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return splitMarkdown(source, resource.getContentAsString(StandardCharsets.UTF_8));
        }

        List<Document> chunks = new ArrayList<>();
        int index = 0;
        for (Document document : new TikaDocumentReader(resource).get()) {
            for (String piece : splitLongText(document.getText())) {
                Map<String, Object> metadata = new HashMap<>(document.getMetadata());
                metadata.put("source", source);
                metadata.put("category", "general");
                metadata.put("section", source);
                metadata.put("topic", source);
                metadata.put("chunk_index", index);
                metadata.put("chunk_id", source + ":" + index);
                chunks.add(buildDocument(source, index++, piece, metadata));
            }
        }
        return chunks;
    }

    List<Document> splitMarkdown(String source, String markdown) {
        ParsedMarkdown parsed = parseFrontMatter(markdown);
        markdown = parsed.body();
        List<Section> sections = new ArrayList<>();
        String title = source;
        String section = "概览";
        String subsection = "";
        StringBuilder body = new StringBuilder();

        for (String line : markdown.split("\\R", -1)) {
            Matcher matcher = HEADING.matcher(line);
            if (!matcher.matches()) {
                body.append(line).append('\n');
                continue;
            }

            int level = matcher.group(1).length();
            String heading = matcher.group(2).trim();
            if (level == 1) {
                title = heading;
                continue;
            }

            flushSection(sections, section, subsection, body);
            if (level == 2) {
                section = heading;
                subsection = "";
            } else {
                subsection = heading;
            }
            body.append(line).append('\n');
        }
        flushSection(sections, section, subsection, body);

        List<Document> chunks = new ArrayList<>();
        int index = 0;
        for (Section item : sections) {
            List<String> pieces = splitLongText(item.text());
            for (int part = 0; part < pieces.size(); part++) {
                String category = "github_trend".equals(parsed.metadata().get("document_type"))
                        ? "trends"
                        : categoryOf(item.section());
                String topic = item.subsection().isBlank() ? item.section() : item.subsection();
                Map<String, Object> metadata = new HashMap<>(parsed.metadata());
                if ("github_trend".equals(metadata.get("document_type"))
                        && !item.section().contains("自动快照")) {
                    Object analysisDate = metadata.get("analysis_date");
                    Object analysisExpiresAt = metadata.get("analysis_expires_at");
                    if (analysisDate != null) metadata.put("snapshot_date", analysisDate);
                    if (analysisExpiresAt != null) metadata.put("expires_at", analysisExpiresAt);
                }
                metadata.put("source", source);
                metadata.put("title", title);
                metadata.put("section", item.section());
                metadata.put("subsection", item.subsection());
                metadata.put("category", category);
                metadata.put("topic", topic);
                metadata.put("project", "projects".equals(category) ? item.subsection() : "");
                metadata.put("keywords", category + "," + item.section() + "," + topic);
                metadata.put("chunk_index", index);
                metadata.put("chunk_part", part);
                metadata.put("chunk_id", source + ":" + index);
                chunks.add(buildDocument(source, index++, pieces.get(part), metadata));
            }
        }
        return chunks;
    }

    private ParsedMarkdown parseFrontMatter(String markdown) {
        Matcher matcher = FRONT_MATTER.matcher(markdown == null ? "" : markdown);
        if (!matcher.find()) return new ParsedMarkdown(Map.of(), markdown == null ? "" : markdown);

        Map<String, Object> metadata = new HashMap<>();
        for (String line : matcher.group(1).split("\\R")) {
            int separator = line.indexOf(':');
            if (separator <= 0) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.isBlank() && !value.isBlank()) metadata.put(key, value);
        }
        return new ParsedMarkdown(Map.copyOf(metadata), markdown.substring(matcher.end()));
    }

    private Document buildDocument(String source, int index, String text, Map<String, Object> metadata) {
        String stableId = UUID.nameUUIDFromBytes(
                (source + ":" + index + ":" + text).getBytes(StandardCharsets.UTF_8)).toString();
        return Document.builder()
                .id(stableId)
                .text(text.trim())
                .metadata(metadata)
                .build();
    }

    private void flushSection(List<Section> sections, String section, String subsection, StringBuilder body) {
        String text = body.toString().trim();
        String substantiveText = text.replaceAll("(?m)^#{1,3}\\s+.+$", "")
                .replaceAll("(?m)^---+$", "")
                .trim();
        if (!substantiveText.isBlank()) {
            sections.add(new Section(section, subsection, text));
        }
        body.setLength(0);
    }

    private List<String> splitLongText(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) return List.of();
        if (normalized.length() <= maxChunkChars) return List.of(normalized);

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : normalized.split("\\R\\s*\\R")) {
            String trimmed = paragraph.trim();
            if (trimmed.isBlank()) continue;
            if (!current.isEmpty() && current.length() + trimmed.length() + 2 > maxChunkChars) {
                result.add(current.toString().trim());
                current.setLength(0);
            }
            if (trimmed.length() > maxChunkChars) {
                if (!current.isEmpty()) {
                    result.add(current.toString().trim());
                    current.setLength(0);
                }
                for (int start = 0; start < trimmed.length(); start += maxChunkChars) {
                    result.add(trimmed.substring(start, Math.min(start + maxChunkChars, trimmed.length())));
                }
            } else {
                if (!current.isEmpty()) current.append("\n\n");
                current.append(trimmed);
            }
        }
        if (!current.isEmpty()) result.add(current.toString().trim());
        return result;
    }

    private String categoryOf(String section) {
        if (section.contains("趋势") || section.contains("热门仓库") || section.contains("Trend")) return "trends";
        if (section.contains("基本信息")) return "basic";
        if (section.contains("教育")) return "education";
        if (section.contains("职业生涯") || section.contains("工作经历")) return "career";
        if (section.contains("项目")) return "projects";
        if (section.contains("技术栈") || section.contains("能力")) return "skills";
        if (section.contains("职业定位")) return "positioning";
        if (section.contains("职业目标")) return "goals";
        if (section.contains("兴趣")) return "interests";
        if (section.contains("理念") || section.contains("性格")) return "personality";
        return "general";
    }

    private record Section(String section, String subsection, String text) {}
    private record ParsedMarkdown(Map<String, Object> metadata, String body) {}
}
