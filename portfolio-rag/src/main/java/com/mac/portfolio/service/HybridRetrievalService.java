package com.mac.portfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HybridRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);
    private static final Pattern LATIN_TOKEN = Pattern.compile("[a-z0-9+#.]{2,}");
    private static final Pattern HAN_RUN = Pattern.compile("[\\p{IsHan}]{2,}");
    private static final Set<String> STOP_TERMS = Set.of(
            "什么", "哪些", "怎么", "如何", "一下", "介绍", "相关", "经验", "可以", "是否", "这个", "那个");

    // 元数据命中了用户意图类目（metadataScore >= 0.75）时给予的额外加分。
    // 背景：短问题的向量相似度往往挤在很窄的区间（如 0.28~0.31），
    // 单纯靠向量分排序会让"问教育却召回技术栈"这类错位发生，这里强制让意图命中的片段跳升。
    private static final double INTENT_MATCH_BOOST = 0.20;
    private static final double VECTOR_WEIGHT = 0.60;
    private static final double LEXICAL_WEIGHT = 0.25;
    private static final double METADATA_WEIGHT = 0.15;

    private final VectorStore vectorStore;
    private final KnowledgeChunkStore chunkStore;
    private final int vectorTopK;
    private final int finalTopK;
    private final double similarityThreshold;
    private final int maxContextChars;
    private final int fullContextMaxChars;

    public HybridRetrievalService(
            VectorStore vectorStore,
            KnowledgeChunkStore chunkStore,
            @Value("${portfolio.rag.vector-top-k:10}") int vectorTopK,
            @Value("${portfolio.rag.final-top-k:3}") int finalTopK,
            @Value("${portfolio.rag.similarity-threshold:0.35}") double similarityThreshold,
            @Value("${portfolio.rag.max-context-chars:2800}") int maxContextChars,
            @Value("${portfolio.rag.full-context-max-chars:8000}") int fullContextMaxChars) {
        this.vectorStore = vectorStore;
        this.chunkStore = chunkStore;
        this.vectorTopK = vectorTopK;
        this.finalTopK = finalTopK;
        this.similarityThreshold = similarityThreshold;
        this.maxContextChars = maxContextChars;
        this.fullContextMaxChars = fullContextMaxChars;
    }

    public List<Document> retrieve(String question) {
        List<Document> snapshot = chunkStore.snapshot();
        int corpusChars = corpusChars(snapshot);
        // 小语料直接给全量上下文：检索切块的收益只有在语料足够大时才体现，
        // 简历这种几千字符的语料整份喂给 LLM 反而更准（也避免 top-k 漏召回）。
        // 语料变大后自动走下面的混合检索，保证可扩展性。
        if (corpusChars <= fullContextMaxChars) {
            log.info("知识库较小（{} 字符 ≤ {}），使用全量上下文而非 top-{} 检索", corpusChars, fullContextMaxChars, finalTopK);
            return fullContext(snapshot);
        }

        IntentProfile intent = IntentProfile.from(question);
        Map<String, Candidate> candidates = new LinkedHashMap<>();

        List<Document> vectorHits = vectorSearch(question, intent.filterExpression());
        if (vectorHits.size() < 2 && intent.filterExpression() != null) {
            mergeVectorHits(candidates, vectorHits);
            vectorHits = vectorSearch(question, null);
        }
        mergeVectorHits(candidates, vectorHits);

        for (Document document : snapshot) {
            double lexicalScore = lexicalScore(question, searchableText(document));
            double metadataScore = intent.metadataScore(document);
            if (lexicalScore >= 0.08 || metadataScore >= 0.75) {
                Candidate candidate = candidates.computeIfAbsent(key(document), ignored -> new Candidate(document));
                candidate.lexicalScore = Math.max(candidate.lexicalScore, lexicalScore);
                candidate.metadataScore = Math.max(candidate.metadataScore, metadataScore);
            }
        }

        for (Candidate candidate : candidates.values()) {
            candidate.lexicalScore = Math.max(candidate.lexicalScore,
                    lexicalScore(question, searchableText(candidate.document)));
            candidate.metadataScore = Math.max(candidate.metadataScore, intent.metadataScore(candidate.document));
            double intentBoost = candidate.metadataScore >= 0.75 ? INTENT_MATCH_BOOST : 0.0;
            candidate.finalScore = candidate.vectorScore * VECTOR_WEIGHT
                    + candidate.lexicalScore * LEXICAL_WEIGHT
                    + candidate.metadataScore * METADATA_WEIGHT
                    + intentBoost;
            if (candidate.vectorScore == 0.0) {
                candidate.finalScore = candidate.lexicalScore * 0.65 + candidate.metadataScore * 0.35 + intentBoost;
            }
        }

        List<Candidate> ranked = candidates.values().stream()
                .filter(candidate -> candidate.finalScore > 0.05)
                .sorted(Comparator.comparingDouble((Candidate value) -> value.finalScore).reversed())
                .toList();

        List<Document> result = new ArrayList<>();
        Set<String> seenTopics = new HashSet<>();
        int contextChars = 0;
        for (Candidate candidate : ranked) {
            if (result.size() >= finalTopK) break;
            String topic = String.valueOf(candidate.document.getMetadata().getOrDefault("topic", ""));
            if (!topic.isBlank() && !seenTopics.add(topic)) continue;

            String text = candidate.document.getText();
            int remaining = maxContextChars - contextChars;
            if (remaining <= 0) break;
            if (text.length() > remaining) text = text.substring(0, remaining);

            Map<String, Object> metadata = new HashMap<>(candidate.document.getMetadata());
            metadata.put("hybrid_score", round(candidate.finalScore));
            result.add(Document.builder()
                    .id(candidate.document.getId())
                    .text(text)
                    .metadata(metadata)
                    .score(round(candidate.finalScore))
                    .build());
            contextChars += text.length();
        }
        return result;
    }

    private int corpusChars(List<Document> snapshot) {
        return snapshot.stream().mapToInt(document -> document.getText().length()).sum();
    }

    // 全量上下文：按入库顺序返回全部片段，分数标记为 1.0，跳过 top-k 与字符截断。
    private List<Document> fullContext(List<Document> snapshot) {
        return snapshot.stream()
                .sorted(Comparator.comparingInt(this::chunkIndex))
                .map(document -> {
                    Map<String, Object> metadata = new HashMap<>(document.getMetadata());
                    metadata.put("hybrid_score", 1.0);
                    return Document.builder()
                            .id(document.getId())
                            .text(document.getText())
                            .metadata(metadata)
                            .score(1.0)
                            .build();
                })
                .toList();
    }

    private int chunkIndex(Document document) {
        Object index = document.getMetadata().get("chunk_index");
        return index instanceof Number number ? number.intValue() : 0;
    }

    private List<Document> vectorSearch(String question, String filterExpression) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(question)
                    .topK(vectorTopK)
                    .similarityThreshold(similarityThreshold);
            if (filterExpression != null) builder.filterExpression(filterExpression);
            List<Document> result = vectorStore.similaritySearch(builder.build());
            return result == null ? List.of() : result;
        } catch (Exception e) {
            log.warn("向量检索失败，降级为本地关键词与元数据检索：{}", e.getMessage());
            return List.of();
        }
    }

    private void mergeVectorHits(Map<String, Candidate> candidates, List<Document> hits) {
        for (Document document : hits) {
            Candidate candidate = candidates.computeIfAbsent(key(document), ignored -> new Candidate(document));
            candidate.vectorScore = Math.max(candidate.vectorScore,
                    document.getScore() == null ? 0.0 : document.getScore());
        }
    }

    private String key(Document document) {
        return String.valueOf(document.getMetadata().getOrDefault("chunk_id", document.getId()));
    }

    private String searchableText(Document document) {
        return document.getText() + " "
                + document.getMetadata().getOrDefault("section", "") + " "
                + document.getMetadata().getOrDefault("topic", "") + " "
                + document.getMetadata().getOrDefault("keywords", "");
    }

    static double lexicalScore(String query, String content) {
        Set<String> queryTerms = terms(query);
        if (queryTerms.isEmpty()) return 0.0;
        Set<String> contentTerms = terms(content);
        long matches = queryTerms.stream().filter(contentTerms::contains).count();
        return Math.min(1.0, matches / (double) queryTerms.size());
    }

    private static Set<String> terms(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Set<String> terms = new HashSet<>();
        Matcher latinMatcher = LATIN_TOKEN.matcher(normalized);
        while (latinMatcher.find()) terms.add(latinMatcher.group());
        Matcher hanMatcher = HAN_RUN.matcher(normalized);
        while (hanMatcher.find()) {
            String run = hanMatcher.group();
            for (int i = 0; i < run.length() - 1; i++) {
                String term = run.substring(i, i + 2);
                if (!STOP_TERMS.contains(term)) terms.add(term);
            }
        }
        return terms;
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private static final class Candidate {
        private final Document document;
        private double vectorScore;
        private double lexicalScore;
        private double metadataScore;
        private double finalScore;

        private Candidate(Document document) {
            this.document = document;
        }
    }

    private record IntentProfile(Set<String> categories, String filterExpression, String normalizedQuestion) {

        private static IntentProfile from(String question) {
            String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
            if (containsAny(normalized, "项目", "作品", "做过", "开发过", "localagent", "trendcopy", "fundlens", "rag")) {
                return new IntentProfile(Set.of("projects"), "category == 'projects'", normalized);
            }
            if (containsAny(normalized, "技术栈", "技术", "能力", "熟悉", "java", "spring", "python", "ai agent")) {
                return new IntentProfile(Set.of("skills", "projects", "career"),
                        "category in ['skills', 'projects', 'career']", normalized);
            }
            if (containsAny(normalized, "工作", "经历", "公司", "道富", "乐歌", "云融")) {
                return new IntentProfile(Set.of("career"), "category == 'career'", normalized);
            }
            if (containsAny(normalized, "教育", "学校", "学历", "大学", "专业")) {
                return new IntentProfile(Set.of("education"), "category == 'education'", normalized);
            }
            if (containsAny(normalized, "联系", "邮箱", "电话", "github", "博客")) {
                return new IntentProfile(Set.of("basic"), "category == 'basic'", normalized);
            }
            if (containsAny(normalized, "兴趣", "性格", "爱好", "理念")) {
                return new IntentProfile(Set.of("interests", "personality"),
                        "category in ['interests', 'personality']", normalized);
            }
            if (containsAny(normalized, "定位", "求职", "目标", "岗位")) {
                return new IntentProfile(Set.of("positioning", "goals"),
                        "category in ['positioning', 'goals']", normalized);
            }
            return new IntentProfile(Set.of(), null, normalized);
        }

        private double metadataScore(Document document) {
            String category = String.valueOf(document.getMetadata().getOrDefault("category", ""));
            String topic = String.valueOf(document.getMetadata().getOrDefault("topic", "")).toLowerCase(Locale.ROOT);
            String project = String.valueOf(document.getMetadata().getOrDefault("project", "")).toLowerCase(Locale.ROOT);
            for (String token : List.of("localagent", "trendcopy", "fundlens", "portfolio", "databricks", "hadoop")) {
                if (normalizedQuestion.contains(token)) {
                    return topic.contains(token) || project.contains(token) ? 1.0 : 0.1;
                }
            }
            return categories.contains(category) ? 0.8 : 0.0;
        }

        private static boolean containsAny(String text, String... values) {
            for (String value : values) if (text.contains(value)) return true;
            return false;
        }
    }
}
