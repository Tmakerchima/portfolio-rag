package com.mac.portfolio.enterprise.ingestion;

import com.mac.portfolio.enterprise.model.EnterpriseChunk;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Token-aware chunker that preserves Markdown sections, paragraphs, lists, and fenced code blocks. */
@Component
public class EnterpriseDocumentChunker {

    public static final String VERSION = "structure-token-v2";
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)");

    private final TokenCountEstimator tokenEstimator;
    private final int maxChunkTokens;
    private final int overlapTokens;

    @Autowired
    public EnterpriseDocumentChunker(
            @Value("${enterprise.rag.max-chunk-tokens:700}") int maxChunkTokens,
            @Value("${enterprise.rag.chunk-overlap-tokens:80}") int overlapTokens) {
        this(new JTokkitTokenCountEstimator(), maxChunkTokens, overlapTokens);
    }

    EnterpriseDocumentChunker(int maxChunkTokens) {
        this(new JTokkitTokenCountEstimator(), maxChunkTokens, 0);
    }

    EnterpriseDocumentChunker(TokenCountEstimator tokenEstimator, int maxChunkTokens, int overlapTokens) {
        if (tokenEstimator == null) throw new IllegalArgumentException("token estimator must not be null");
        if (maxChunkTokens < 32) throw new IllegalArgumentException("max chunk size must be at least 32 tokens");
        if (overlapTokens < 0 || overlapTokens >= maxChunkTokens) {
            throw new IllegalArgumentException("chunk overlap must be non-negative and smaller than chunk size");
        }
        this.tokenEstimator = tokenEstimator;
        this.maxChunkTokens = maxChunkTokens;
        this.overlapTokens = overlapTokens;
    }

    public List<EnterpriseChunk> chunk(EnterpriseDocumentInput input) {
        String normalized = normalize(input.content());
        if (normalized.isBlank()) return List.of();

        List<Piece> pieces = new ArrayList<>();
        for (Block block : parseBlocks(normalized, input.title())) {
            pieces.addAll(splitBlock(block));
        }
        List<Draft> drafts = pack(pieces);

        String documentKey = input.source() + ":" + input.externalId();
        List<EnterpriseChunk> result = new ArrayList<>(drafts.size());
        for (int index = 0; index < drafts.size(); index++) {
            Draft draft = drafts.get(index);
            String content = draft.content().trim();
            String chunkId = UUID.nameUUIDFromBytes(
                    (documentKey + ":" + index + ":" + sha256(content)).getBytes(StandardCharsets.UTF_8))
                    .toString();
            result.add(new EnterpriseChunk(chunkId, index, content, draft.sectionPath(), tokens(content)));
        }
        return List.copyOf(result);
    }

    public String fingerprint() {
        return VERSION + ":max-" + maxChunkTokens + ":overlap-" + overlapTokens;
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

    private List<Block> parseBlocks(String content, String documentTitle) {
        List<Block> blocks = new ArrayList<>();
        String[] headings = new String[6];
        String sectionPath = cleanTitle(documentTitle);
        StringBuilder current = new StringBuilder();
        boolean fenced = false;
        String activeFence = "";

        for (String line : content.split("\n", -1)) {
            Matcher fenceMatcher = FENCE.matcher(line);
            Matcher headingMatcher = HEADING.matcher(line);
            if (!fenced && headingMatcher.matches()) {
                flushBlock(blocks, current, sectionPath);
                int level = headingMatcher.group(1).length();
                headings[level - 1] = headingMatcher.group(2).trim();
                Arrays.fill(headings, level, headings.length, null);
                sectionPath = sectionPath(documentTitle, headings);
                current.append(line);
                continue;
            }
            if (!fenced && line.isBlank()) {
                flushBlock(blocks, current, sectionPath);
                continue;
            }
            if (!current.isEmpty()) current.append('\n');
            current.append(line);
            if (fenceMatcher.find()) {
                String marker = fenceMatcher.group(1);
                if (!fenced) {
                    fenced = true;
                    activeFence = marker;
                } else if (marker.equals(activeFence)) {
                    fenced = false;
                    activeFence = "";
                }
            }
        }
        flushBlock(blocks, current, sectionPath);
        return blocks;
    }

    private void flushBlock(List<Block> blocks, StringBuilder current, String sectionPath) {
        String value = current.toString().trim();
        if (!value.isBlank()) blocks.add(new Block(sectionPath, value));
        current.setLength(0);
    }

    private String sectionPath(String documentTitle, String[] headings) {
        List<String> values = new ArrayList<>();
        String title = cleanTitle(documentTitle);
        if (!title.isBlank()) values.add(title);
        for (String heading : headings) {
            if (heading != null && !heading.isBlank()
                    && (values.isEmpty() || !values.getLast().equalsIgnoreCase(heading))) {
                values.add(heading);
            }
        }
        return String.join(" > ", values);
    }

    private String cleanTitle(String value) {
        return value == null ? "" : value.trim();
    }

    private List<Piece> splitBlock(Block block) {
        if (tokens(block.content()) <= maxChunkTokens) {
            return List.of(new Piece(block.sectionPath(), block.content()));
        }

        List<String> sentences = sentences(block.content());
        List<Piece> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (tokens(sentence) > maxChunkTokens) {
                if (!current.isEmpty()) {
                    result.add(new Piece(block.sectionPath(), current.toString().trim()));
                    current.setLength(0);
                }
                for (String part : hardSplit(sentence)) result.add(new Piece(block.sectionPath(), part));
                continue;
            }
            String candidate = current.isEmpty() ? sentence : current + " " + sentence;
            if (!current.isEmpty() && tokens(candidate) > maxChunkTokens) {
                result.add(new Piece(block.sectionPath(), current.toString().trim()));
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(' ');
            current.append(sentence);
        }
        if (!current.isEmpty()) result.add(new Piece(block.sectionPath(), current.toString().trim()));
        return result;
    }

    private List<String> sentences(String value) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
        iterator.setText(value);
        List<String> result = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = value.substring(start, end).trim();
            if (!sentence.isBlank()) result.add(sentence);
        }
        return result.isEmpty() ? List.of(value) : result;
    }

    private List<String> hardSplit(String value) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < value.length()) {
            int end = largestEndWithinTokens(value, start, maxChunkTokens);
            if (end <= start) end = nextCodePointEnd(value, start);
            int preferred = preferredBoundary(value, start, end);
            if (preferred > start) end = preferred;
            String part = value.substring(start, end).trim();
            if (!part.isBlank()) result.add(part);
            start = end;
            while (start < value.length() && Character.isWhitespace(value.charAt(start))) start++;
        }
        return result;
    }

    private List<Draft> pack(List<Piece> pieces) {
        List<Draft> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentSection = "";
        for (Piece piece : pieces) {
            if (current.isEmpty()) {
                current.append(piece.content());
                currentSection = piece.sectionPath();
                continue;
            }
            boolean sameSection = currentSection.equals(piece.sectionPath());
            String candidate = current + "\n\n" + piece.content();
            if (sameSection && tokens(candidate) <= maxChunkTokens) {
                current.append("\n\n").append(piece.content());
                continue;
            }

            String completed = current.toString().trim();
            result.add(new Draft(currentSection, completed));
            current.setLength(0);
            currentSection = piece.sectionPath();
            if (sameSection && overlapTokens > 0) {
                int available = Math.max(0, maxChunkTokens - tokens(piece.content()) - 2);
                String overlap = tailWithinTokens(completed, Math.min(overlapTokens, available));
                if (!overlap.isBlank()) current.append(overlap).append("\n\n");
            }
            current.append(piece.content());
        }
        if (!current.isEmpty()) result.add(new Draft(currentSection, current.toString().trim()));
        return result;
    }

    private String tailWithinTokens(String value, int budget) {
        if (budget <= 0 || value.isBlank()) return "";
        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (tokens(value.substring(mid)) <= budget) high = mid;
            else low = mid + 1;
        }
        int start = Math.min(low, value.length());
        int scanEnd = Math.min(value.length(), start + Math.max(24, (value.length() - start) / 3));
        while (start < scanEnd && !Character.isWhitespace(value.charAt(start))) start++;
        return value.substring(Math.min(start, value.length())).trim();
    }

    private int largestEndWithinTokens(String value, int start, int budget) {
        int low = nextCodePointEnd(value, start);
        int high = value.length();
        int best = start;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (mid < value.length() && Character.isLowSurrogate(value.charAt(mid))) mid--;
            if (mid <= start) mid = nextCodePointEnd(value, start);
            if (tokens(value.substring(start, mid)) <= budget) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private int preferredBoundary(String value, int start, int end) {
        int minimum = start + (int) ((end - start) * 0.7);
        for (int index = end - 1; index >= minimum; index--) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character) || ".!?。！？;；".indexOf(character) >= 0) {
                return index + 1;
            }
        }
        return end;
    }

    private int nextCodePointEnd(String value, int start) {
        if (start >= value.length()) return value.length();
        return start + Character.charCount(value.codePointAt(start));
    }

    private int tokens(String value) {
        return value == null || value.isBlank() ? 0 : tokenEstimator.estimate(value);
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

    private record Block(String sectionPath, String content) {}
    private record Piece(String sectionPath, String content) {}
    private record Draft(String sectionPath, String content) {}
}
