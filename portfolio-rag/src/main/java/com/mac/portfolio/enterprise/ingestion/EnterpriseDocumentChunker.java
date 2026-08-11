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

/**
 * Enterprise 文档的 Token 感知切块器。
 *
 * <p>先按 Markdown 标题、空行段落和代码围栏建立结构块，再把超长结构块切小，
 * 最后在同一章节内打包并加入有限重叠。它只处理文本，不负责读取 PDF/JPG 文件。</p>
 */
@Component
public class EnterpriseDocumentChunker {

    /** 切块算法版本；算法或关键参数变化后会参与索引指纹，触发重新入库。 */
    public static final String VERSION = "structure-token-v2";
    /** 只识别标准 Markdown 的 1～6 级 ATX 标题，例如 ## 退款流程。 */
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*$");
    /** 识别 ``` 或 ~~~ 代码围栏，避免代码内部的空行被误当成段落边界。 */
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)");

    private final TokenCountEstimator tokenEstimator;
    private final int maxChunkTokens;
    private final int overlapTokens;

    @Autowired
    public EnterpriseDocumentChunker(
            @Value("${enterprise.rag.max-chunk-tokens:700}") int maxChunkTokens,
            @Value("${enterprise.rag.chunk-overlap-tokens:80}") int overlapTokens) {
        // Spring 创建组件时从配置读取 700/80，并使用 JTokkit 估算 Token 数。
        this(new JTokkitTokenCountEstimator(), maxChunkTokens, overlapTokens);
    }

    EnterpriseDocumentChunker(int maxChunkTokens) {
        this(new JTokkitTokenCountEstimator(), maxChunkTokens, 0);
    }

    EnterpriseDocumentChunker(TokenCountEstimator tokenEstimator, int maxChunkTokens, int overlapTokens) {
        // 在应用启动阶段拒绝不可能成立的配置，避免入库到一半才失败。
        if (tokenEstimator == null) throw new IllegalArgumentException("token estimator must not be null");
        if (maxChunkTokens < 32) throw new IllegalArgumentException("max chunk size must be at least 32 tokens");
        if (overlapTokens < 0 || overlapTokens >= maxChunkTokens) {
            throw new IllegalArgumentException("chunk overlap must be non-negative and smaller than chunk size");
        }
        this.tokenEstimator = tokenEstimator;
        this.maxChunkTokens = maxChunkTokens;
        this.overlapTokens = overlapTokens;
    }

    /** 把一份已提取成文本的文档转换为有顺序、可引用的最终 Chunk 列表。 */
    public List<EnterpriseChunk> chunk(EnterpriseDocumentInput input) {
        // 步骤 1：先消除换行/BOM 等格式差异；空正文没有可建立的索引。
        String normalized = normalize(input.content());
        if (normalized.isBlank()) return List.of();

        // 步骤 2：Block 保留结构；Piece 保证单块不超过最大 Token。
        List<Piece> pieces = new ArrayList<>();
        for (Block block : parseBlocks(normalized, input.title())) {
            pieces.addAll(splitBlock(block));
        }

        // 步骤 3：把相邻 Piece 打包为最终大小，并只在同章节内加入重叠。
        List<Draft> drafts = pack(pieces);

        // 步骤 4：使用来源身份、顺序和内容哈希生成稳定 Chunk ID。
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

    /** 描述切块算法和参数；该值变化会让未修改正文也重新建立索引。 */
    public String fingerprint() {
        return VERSION + ":max-" + maxChunkTokens + ":overlap-" + overlapTokens;
    }

    /** 统一文本格式，让内容哈希和切块结果不受操作系统换行差异影响。 */
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

    /**
     * 按行扫描文本并产生结构 Block。
     * 标题会更新章节栈，普通空行会结束段落，代码围栏内部的空行则保持原样。
     */
    private List<Block> parseBlocks(String content, String documentTitle) {
        List<Block> blocks = new ArrayList<>();
        // 数组下标 0～5 分别保存当前 # 到 ###### 标题。
        String[] headings = new String[6];
        // 没有任何 Markdown 标题时，整份文档使用 documentTitle 作为章节路径。
        String sectionPath = cleanTitle(documentTitle);
        StringBuilder current = new StringBuilder();
        boolean fenced = false;
        String activeFence = "";

        for (String line : content.split("\n", -1)) {
            Matcher fenceMatcher = FENCE.matcher(line);
            Matcher headingMatcher = HEADING.matcher(line);
            if (!fenced && headingMatcher.matches()) {
                // 新标题出现前，先把旧章节尚未提交的内容保存下来。
                flushBlock(blocks, current, sectionPath);
                int level = headingMatcher.group(1).length();
                headings[level - 1] = headingMatcher.group(2).trim();
                // 例如遇到新的 ## 时，清除旧的 ###～###### 子标题，防止章节路径串线。
                Arrays.fill(headings, level, headings.length, null);
                sectionPath = sectionPath(documentTitle, headings);
                // 标题行本身也保留在正文中，Embedding 不只依赖 metadata。
                current.append(line);
                continue;
            }
            if (!fenced && line.isBlank()) {
                // 普通空行表示段落结束；代码块内部空行不会进入这个分支。
                flushBlock(blocks, current, sectionPath);
                continue;
            }
            if (!current.isEmpty()) current.append('\n');
            current.append(line);
            if (fenceMatcher.find()) {
                String marker = fenceMatcher.group(1);
                if (!fenced) {
                    // 记录具体围栏类型，确保 ``` 只能由 ``` 关闭，~~~ 同理。
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

    /** 把当前缓冲区提交为 Block，并清空缓冲区供下一段使用。 */
    private void flushBlock(List<Block> blocks, StringBuilder current, String sectionPath) {
        String value = current.toString().trim();
        if (!value.isBlank()) blocks.add(new Block(sectionPath, value));
        current.setLength(0);
    }

    /** 把文档标题与当前有效的多级 Markdown 标题拼成可检索章节路径。 */
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

    /** 将单个超长 Block 优先按句子拆成不超过 maxChunkTokens 的 Piece。 */
    private List<Piece> splitBlock(Block block) {
        // 小 Block 保持完整，避免为了追求固定大小破坏语义结构。
        if (tokens(block.content()) <= maxChunkTokens) {
            return List.of(new Piece(block.sectionPath(), block.content()));
        }

        List<String> sentences = sentences(block.content());
        List<Piece> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (tokens(sentence) > maxChunkTokens) {
                // 单句已经超限时，先提交前面积累的句子，再对该句执行硬切。
                if (!current.isEmpty()) {
                    result.add(new Piece(block.sectionPath(), current.toString().trim()));
                    current.setLength(0);
                }
                for (String part : hardSplit(sentence)) result.add(new Piece(block.sectionPath(), part));
                continue;
            }
            String candidate = current.isEmpty() ? sentence : current + " " + sentence;
            if (!current.isEmpty() && tokens(candidate) > maxChunkTokens) {
                // 加入下一句会超限：结束当前 Piece，让下一句进入新 Piece。
                result.add(new Piece(block.sectionPath(), current.toString().trim()));
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(' ');
            current.append(sentence);
        }
        if (!current.isEmpty()) result.add(new Piece(block.sectionPath(), current.toString().trim()));
        return result;
    }

    /** 使用 Java 句子边界算法切句；识别不到边界时退回整个原字符串。 */
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

    /**
     * 最后兜底的硬切：二分查找 Token 预算允许的最大字符位置，
     * 再尽量向前调整到空格或中英文标点，减少截断词语的概率。
     */
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

    /**
     * 将 Piece 打包为最终 Draft。同一章节且合并后不超限才合并；
     * 换章节一定开新 Chunk，同章节溢出时才复制上一块尾部作为 overlap。
     */
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
                // 同章节还有空间，保留段落之间的双换行后继续合并。
                current.append("\n\n").append(piece.content());
                continue;
            }

            String completed = current.toString().trim();
            result.add(new Draft(currentSection, completed));
            current.setLength(0);
            currentSection = piece.sectionPath();
            if (sameSection && overlapTokens > 0) {
                // overlap 会让位给新 Piece，保证“重叠 + 新正文”仍不超过最大 Token。
                int available = Math.max(0, maxChunkTokens - tokens(piece.content()) - 2);
                String overlap = tailWithinTokens(completed, Math.min(overlapTokens, available));
                if (!overlap.isBlank()) current.append(overlap).append("\n\n");
            }
            current.append(piece.content());
        }
        if (!current.isEmpty()) result.add(new Draft(currentSection, current.toString().trim()));
        return result;
    }

    /** 从上一 Chunk 尾部截取不超过预算的重叠文本，并尽量从完整词边界开始。 */
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

    /** 二分查找从 start 开始、Token 数不超过 budget 的最远字符位置。 */
    private int largestEndWithinTokens(String value, int start, int budget) {
        int low = nextCodePointEnd(value, start);
        int high = value.length();
        int best = start;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            // 不允许把 Emoji 等补充字符的 UTF-16 代理对切成两半。
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

    /** 在允许范围后 30% 内向前寻找更自然的空格或标点切分点。 */
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

    /** 返回一个完整 Unicode code point 的结束位置，避免硬切时破坏代理对。 */
    private int nextCodePointEnd(String value, int start) {
        if (start >= value.length()) return value.length();
        return start + Character.charCount(value.codePointAt(start));
    }

    /** 对空文本返回 0，其余文本交给配置的 TokenCountEstimator 估算。 */
    private int tokens(String value) {
        return value == null || value.isBlank() ? 0 : tokenEstimator.estimate(value);
    }

    /** 生成稳定 SHA-256 十六进制摘要，用于内容去重和 Chunk ID。 */
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

    /** 原始结构块：标题/段落/列表/代码围栏等连续内容。 */
    private record Block(String sectionPath, String content) {}
    /** 已确保自身不超过 Token 上限、等待打包的小块。 */
    private record Piece(String sectionPath, String content) {}
    /** 已完成章节打包和重叠处理、等待生成稳定 ID 的最终草稿。 */
    private record Draft(String sectionPath, String content) {}
}
