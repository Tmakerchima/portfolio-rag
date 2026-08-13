package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;
import com.mac.portfolio.enterprise.model.EnterpriseRetrievalStrategy;
import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;
import com.mac.portfolio.enterprise.repository.EnterpriseDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EnterpriseRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseRetrievalService.class);

    private final EnterpriseDocumentRepository repository;
    private final EnterpriseLexicalRetriever lexicalRetriever;
    private final EmbeddingModel embeddingModel;
    private final RrfFusion rrfFusion;
    private final Reranker reranker;
    private final int vectorTopK;
    private final int keywordTopK;
    private final int finalTopK;
    private final int rrfK;
    private final int maxContextChars;
    private final int maxContextTokens;
    private final int maxChunksPerDocument;
    private final double similarityThreshold;
    private final boolean rerankEnabled;
    private final TokenCountEstimator tokenEstimator;

    /**
     * 兼容旧的单元测试/内部调用：旧构造器自动包一层 PostgreSQL FTS。
     * Spring 生产构造器使用下面带 lexicalRetriever 的版本，因此 KEYWORD 仍可切到 BM25。
     */
    public EnterpriseRetrievalService(
            EnterpriseDocumentRepository repository,
            EmbeddingModel embeddingModel,
            RrfFusion rrfFusion,
            Reranker reranker,
            int vectorTopK,
            int keywordTopK,
            int finalTopK,
            int rrfK,
            int maxContextChars,
            double similarityThreshold,
            boolean rerankEnabled) {
        this(repository, new PostgresFtsLexicalRetriever(repository), embeddingModel, rrfFusion, reranker,
                vectorTopK, keywordTopK, finalTopK, rrfK, maxContextChars, 2200, 2,
                similarityThreshold, rerankEnabled);
    }

    @Autowired
    public EnterpriseRetrievalService(
            EnterpriseDocumentRepository repository,
            EnterpriseLexicalRetriever lexicalRetriever,
            EmbeddingModel embeddingModel,
            RrfFusion rrfFusion,
            Reranker reranker,
            @Value("${enterprise.rag.vector-top-k:12}") int vectorTopK,
            @Value("${enterprise.rag.keyword-top-k:12}") int keywordTopK,
            @Value("${enterprise.rag.final-top-k:5}") int finalTopK,
            @Value("${enterprise.rag.rrf-k:60}") int rrfK,
            @Value("${enterprise.rag.max-context-chars:9000}") int maxContextChars,
            @Value("${enterprise.rag.max-context-tokens:2200}") int maxContextTokens,
            @Value("${enterprise.rag.max-chunks-per-document:2}") int maxChunksPerDocument,
            @Value("${enterprise.rag.similarity-threshold:0.20}") double similarityThreshold,
            @Value("${enterprise.rag.rerank-enabled:true}") boolean rerankEnabled) {
        this.repository = repository;
        this.lexicalRetriever = lexicalRetriever;
        this.embeddingModel = embeddingModel;
        this.rrfFusion = rrfFusion;
        this.reranker = reranker;
        this.vectorTopK = vectorTopK;
        this.keywordTopK = keywordTopK;
        this.finalTopK = finalTopK;
        this.rrfK = rrfK;
        this.maxContextChars = maxContextChars;
        if (maxContextTokens < 128) throw new IllegalArgumentException("max context tokens must be at least 128");
        if (maxChunksPerDocument < 1) throw new IllegalArgumentException("max chunks per document must be positive");
        this.maxContextTokens = maxContextTokens;
        this.maxChunksPerDocument = maxChunksPerDocument;
        this.similarityThreshold = similarityThreshold;
        this.rerankEnabled = rerankEnabled;
        this.tokenEstimator = new JTokkitTokenCountEstimator();
    }

    /**
     * 执行一次受 ACL 约束的候选召回。
     *
     * <p>VECTOR 使用 query embedding + pgvector，KEYWORD 使用当前配置的 lexical backend，
     * HYBRID 对两路排名执行 RRF，HYBRID_RERANK 再对有限候选执行 reranker。
     * 各路 repository 查询必须在数据库层筛选 ACTIVE corpus、软删除和权限字段。</p>
     */
    public EnterpriseRetrievalResult retrieve(String query, EnterpriseAccessContext access,
                                              EnterpriseRetrievalStrategy strategy) {
        String safeQuery = query == null ? "" : query.trim();
        List<EnterpriseSearchHit> vectorHits = List.of();
        List<EnterpriseSearchHit> keywordHits = List.of();
        EnterpriseLexicalSearchResult lexicalResult = EnterpriseLexicalSearchResult.of(List.of(), "NOT_USED");
        boolean vectorFailed = false;
        boolean keywordFailed = false;
        boolean rerankerFailed = false;
        long vectorMs = 0;
        long ftsMs = 0;

        // 只有需要语义检索的策略才调用 Embedding；向量失败时记录 fallback，不伪造结果。
        if (strategy == EnterpriseRetrievalStrategy.VECTOR || strategy == EnterpriseRetrievalStrategy.HYBRID
                || strategy == EnterpriseRetrievalStrategy.HYBRID_RERANK) {
            long started = System.nanoTime();
            try {
                vectorHits = repository.searchVector(embeddingModel.embed(safeQuery), access, vectorTopK,
                        similarityThreshold);
            } catch (Exception e) {
                vectorFailed = true;
                log.warn("Enterprise vector retrieval failed; continuing with keyword fallback: {}", e.getMessage());
            } finally {
                vectorMs = elapsedMs(started);
            }
        }

        // KEYWORD 代表配置的 lexical backend：当前生产是 PostgreSQL FTS，未来可切 ParadeDB BM25。
        if (strategy == EnterpriseRetrievalStrategy.KEYWORD || strategy == EnterpriseRetrievalStrategy.HYBRID
                || strategy == EnterpriseRetrievalStrategy.HYBRID_RERANK) {
            long started = System.nanoTime();
            try {
                // KEYWORD 在这里代表“配置的 lexical backend”：生产可为 BM25，故不再直接调用 FTS repository。
                lexicalResult = lexicalRetriever.search(safeQuery, access, keywordTopK);
                keywordHits = lexicalResult.hits();
            } catch (Exception e) {
                keywordFailed = true;
                log.warn("Enterprise lexical retrieval failed; continuing with vector fallback: {}", e.getMessage());
            } finally {
                ftsMs = elapsedMs(started);
            }
        }

        // 向量 cosine 分数与 FTS/BM25 分数不在同一量纲，混合时只融合排名而不直接相加分数。
        long rrfStarted = System.nanoTime();
        List<EnterpriseSearchHit> candidates;
        if (strategy == EnterpriseRetrievalStrategy.VECTOR) candidates = vectorHits;
        else if (strategy == EnterpriseRetrievalStrategy.KEYWORD) candidates = keywordHits;
        // Vector cosine score 与 BM25 score 不在同一数值尺度，不能直接加权；混合策略只按名次做 RRF。
        else candidates = rrfFusion.fuse(vectorHits, keywordHits, rrfK, Math.max(finalTopK * 3, finalTopK));
        long rrfMs = elapsedMs(rrfStarted);

        // reranker 只处理已经完成 ACL 过滤的有限候选；失败时保留 RRF 顺序。
        long rerankStarted = System.nanoTime();
        if (strategy == EnterpriseRetrievalStrategy.HYBRID_RERANK && rerankEnabled) {
            try {
                candidates = reranker.rerank(safeQuery, candidates);
            } catch (Exception e) {
                rerankerFailed = true;
                log.warn("Enterprise reranker failed; using RRF result: {}", e.getMessage());
            }
        }
        long rerankMs = strategy == EnterpriseRetrievalStrategy.HYBRID_RERANK && rerankEnabled ? elapsedMs(rerankStarted) : 0;

        // 所有候选在这里已经过 ACL；只截断原始 content，绝不把 contextual prefix 送进回答证据。
        List<EnterpriseSearchHit> finalHits = limitContext(candidates);
        String retrievalFallback = fallback(vectorFailed, keywordFailed, rerankerFailed);
        retrievalFallback = mergeFallback(retrievalFallback, lexicalResult.fallbackReason());
        return new EnterpriseRetrievalResult(finalHits, strategy,
                new EnterpriseRetrievalMetrics(vectorMs, ftsMs, rrfMs, rerankMs,
                        distinctCount(vectorHits, keywordHits), finalHits.size(),
                        retrievalFallback, 1, lexicalResult.backend(),
                        contextTokenCount(finalHits), distinctDocuments(finalHits)));
    }

    /** Merges optional rewritten-query results while enforcing exactly the same access context. */
    public EnterpriseRetrievalResult expand(EnterpriseRetrievalResult primary,
                                            String originalQuery,
                                            List<String> rewrittenQueries,
                                            EnterpriseAccessContext access,
                                            EnterpriseRetrievalStrategy strategy) {
        if (rewrittenQueries == null || rewrittenQueries.isEmpty()) return primary;
        List<EnterpriseRetrievalResult> results = new ArrayList<>();
        results.add(primary);
        rewrittenQueries.stream()
                .filter(query -> query != null && !query.isBlank())
                .map(String::trim)
                .filter(query -> !query.equalsIgnoreCase(originalQuery))
                .distinct()
                .forEach(query -> results.add(retrieve(query, access, strategy)));
        if (results.size() == 1) return primary;

        long rrfStarted = System.nanoTime();
        List<EnterpriseSearchHit> merged = rrfFusion.fuseAll(
                results.stream().map(EnterpriseRetrievalResult::hits).toList(),
                rrfK, Math.max(finalTopK * 3, finalTopK));
        long mergeMs = elapsedMs(rrfStarted);
        long rerankStarted = System.nanoTime();
        if (strategy == EnterpriseRetrievalStrategy.HYBRID_RERANK && rerankEnabled) {
            try {
                merged = reranker.rerank(originalQuery, merged);
            } catch (Exception error) {
                log.warn("Expanded Enterprise reranker failed; using fused result: {}", error.getMessage());
            }
        }
        long mergeRerankMs = strategy == EnterpriseRetrievalStrategy.HYBRID_RERANK && rerankEnabled
                ? elapsedMs(rerankStarted) : 0;
        List<EnterpriseSearchHit> finalHits = limitContext(merged);
        EnterpriseRetrievalMetrics metrics = new EnterpriseRetrievalMetrics(
                results.stream().mapToLong(result -> result.metrics().vectorMs()).sum(),
                results.stream().mapToLong(result -> result.metrics().ftsMs()).sum(),
                results.stream().mapToLong(result -> result.metrics().rrfMs()).sum() + mergeMs,
                results.stream().mapToLong(result -> result.metrics().rerankMs()).sum() + mergeRerankMs,
                (int) results.stream().flatMap(result -> result.hits().stream())
                        .map(EnterpriseSearchHit::chunkId).distinct().count(),
                finalHits.size(), combineFallback(results), results.size(), combineLexicalBackend(results),
                contextTokenCount(finalHits), distinctDocuments(finalHits));
        return new EnterpriseRetrievalResult(finalHits, strategy, metrics);
    }

    private String combineLexicalBackend(List<EnterpriseRetrievalResult> results) {
        return results.stream().map(result -> result.metrics().lexicalBackend())
                .filter(value -> value != null && !value.isBlank())
                .distinct().reduce((left, right) -> left + "," + right).orElse("NOT_USED");
    }

    private String combineFallback(List<EnterpriseRetrievalResult> results) {
        return results.stream().map(result -> result.metrics().fallback())
                .filter(value -> value != null && !value.isBlank())
                .distinct().reduce((left, right) -> left + "," + right).orElse(null);
    }

    private String fallback(boolean vectorFailed, boolean keywordFailed, boolean rerankerFailed) {
        if (vectorFailed && keywordFailed) return "EVIDENCE_ONLY";
        if (vectorFailed) return "FTS_ONLY";
        if (keywordFailed) return "VECTOR_ONLY";
        if (rerankerFailed) return "RRF";
        return null;
    }

    private String mergeFallback(String primary, String lexical) {
        if (lexical == null || lexical.isBlank()) return primary;
        if (primary == null || primary.isBlank()) return lexical;
        if (primary.contains(lexical)) return primary;
        return primary + "," + lexical;
    }

    private List<EnterpriseSearchHit> limitContext(List<EnterpriseSearchHit> candidates) {
        List<EnterpriseSearchHit> result = new ArrayList<>();
        int chars = 0;
        int tokens = 0;
        java.util.Map<String, Integer> perDocument = new java.util.HashMap<>();
        for (EnterpriseSearchHit candidate : candidates) {
            if (result.size() >= finalTopK || chars >= maxContextChars) break;
            int documentCount = perDocument.getOrDefault(candidate.documentId(), 0);
            if (documentCount >= maxChunksPerDocument) continue;
            int remaining = maxContextChars - chars;
            String content = candidate.content();
            if (content.length() > remaining) content = content.substring(0, remaining);
            if (content.isBlank()) continue;
            int candidateTokens = tokenEstimator.estimate(content);
            if (candidateTokens <= 0 || tokens + candidateTokens > maxContextTokens) {
                int tokenBudget = maxContextTokens - tokens;
                if (tokenBudget <= 0) break;
                content = truncateToTokens(content, tokenBudget);
                candidateTokens = tokenEstimator.estimate(content);
            }
            if (content.isBlank() || candidateTokens <= 0) continue;
            result.add(new EnterpriseSearchHit(candidate.chunkId(), candidate.documentId(), candidate.externalId(),
                    candidate.source(), candidate.sourceType(), candidate.title(), content, candidate.tenantId(),
                    candidate.department(), candidate.accessLevel(), candidate.chunkIndex(), candidate.score(),
                    result.size() + 1, candidate.metadata()));
            chars += content.length();
            tokens += candidateTokens;
            perDocument.merge(candidate.documentId(), 1, Integer::sum);
        }
        return List.copyOf(result);
    }

    private String truncateToTokens(String value, int budget) {
        if (tokenEstimator.estimate(value) <= budget) return value;
        int low = 1;
        int high = value.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            String candidate = value.substring(0, mid);
            if (tokenEstimator.estimate(candidate) <= budget) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best == 0 ? "" : value.substring(0, best).stripTrailing();
    }

    private int contextTokenCount(List<EnterpriseSearchHit> hits) {
        return hits.stream().mapToInt(hit -> tokenEstimator.estimate(hit.content())).sum();
    }

    private int distinctDocuments(List<EnterpriseSearchHit> hits) {
        return (int) hits.stream().map(EnterpriseSearchHit::documentId).distinct().count();
    }

    private int distinctCount(List<EnterpriseSearchHit> vectorHits, List<EnterpriseSearchHit> keywordHits) {
        return (int) java.util.stream.Stream.concat(vectorHits.stream(), keywordHits.stream())
                .map(EnterpriseSearchHit::chunkId).distinct().count();
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
