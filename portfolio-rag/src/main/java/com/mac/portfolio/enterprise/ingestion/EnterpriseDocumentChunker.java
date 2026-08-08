package com.mac.portfolio.enterprise.ingestion;

import com.mac.portfolio.enterprise.model.EnterpriseChunk;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Paragraph-aware chunker for exported EnterpriseRAG-Bench text documents. */
@Component
public class EnterpriseDocumentChunker {

    private final int maxChunkChars;

    public EnterpriseDocumentChunker(@Value("${enterprise.rag.max-chunk-chars:1600}") int maxChunkChars) {
        if (maxChunkChars < 200) throw new IllegalArgumentException("max chunk size must be at least 200");
        this.maxChunkChars = maxChunkChars;
    }

    public List<EnterpriseChunk> chunk(EnterpriseDocumentInput input) {
        String normalized = normalize(input.content());
        if (normalized.isBlank()) return List.of();

        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : normalized.split("\\R\\s*\\R")) {
            String part = paragraph.trim();
            if (part.isBlank()) continue;
            appendPart(pieces, current, part);
        }
        if (!current.isEmpty()) pieces.add(current.toString().trim());

        String documentKey = input.source() + ":" + input.externalId();
        List<EnterpriseChunk> result = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            String content = pieces.get(i);
            String chunkId = UUID.nameUUIDFromBytes(
                    (documentKey + ":" + i + ":" + sha256(content)).getBytes(StandardCharsets.UTF_8)).toString();
            result.add(new EnterpriseChunk(chunkId, i, content));
        }
        return List.copyOf(result);
    }

    static String normalize(String content) {
        return (content == null ? "" : content)
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::stripTrailing)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("")
                .trim();
    }

    private void appendPart(List<String> pieces, StringBuilder current, String part) {
        if (part.length() <= maxChunkChars) {
            if (!current.isEmpty() && current.length() + part.length() + 2 > maxChunkChars) {
                pieces.add(current.toString().trim());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append("\n\n");
            current.append(part);
            return;
        }

        if (!current.isEmpty()) {
            pieces.add(current.toString().trim());
            current.setLength(0);
        }
        for (int start = 0; start < part.length(); start += maxChunkChars) {
            pieces.add(part.substring(start, Math.min(start + maxChunkChars, part.length())).trim());
        }
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
