package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;
import com.mac.portfolio.enterprise.model.EnterpriseRetrievalStrategy;
import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;
import com.mac.portfolio.enterprise.repository.EnterpriseDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EnterpriseRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseRetrievalService.class);

    private final EnterpriseDocumentRepository repository;
    private final EmbeddingModel embeddingModel;
    private final RrfFusion rrfFusion;
    private final Reranker reranker;
    private final int vectorTopK;
    private final int keywordTopK;
    private final int finalTopK;
    private final int rrfK;
    private final int maxContextChars;
    private final double similarityThreshold;

    public EnterpriseRetrievalService(
            EnterpriseDocumentRepository repository,
            EmbeddingModel embeddingModel,
            RrfFusion rrfFusion,
            Reranker reranker,
            @Value("${enterprise.rag.vector-top-k:12}") int vectorTopK,
            @Value("${enterprise.rag.keyword-top-k:12}") int keywordTopK,
            @Value("${enterprise.rag.final-top-k:5}") int finalTopK,
            @Value("${enterprise.rag.rrf-k:60}") int rrfK,
            @Value("${enterprise.rag.max-context-chars:9000}") int maxContextChars,
            @Value("${enterprise.rag.similarity-threshold:0.20}") double similarityThreshold) {
        this.repository = repository;
        this.embeddingModel = embeddingModel;
        this.rrfFusion = rrfFusion;
        this.reranker = reranker;
        this.vectorTopK = vectorTopK;
        this.keywordTopK = keywordTopK;
        this.finalTopK = finalTopK;
        this.rrfK = rrfK;
        this.maxContextChars = maxContextChars;
        this.similarityThreshold = similarityThreshold;
    }

    public EnterpriseRetrievalResult retrieve(String query, EnterpriseAccessContext access,
                                              EnterpriseRetrievalStrategy strategy) {
        String safeQuery = query == null ? "" : query.trim();
        List<EnterpriseSearchHit> vectorHits = List.of();
        List<EnterpriseSearchHit> keywordHits = List.of();
        long vectorMs = 0;
        long ftsMs = 0;

        if (strategy == EnterpriseRetrievalStrategy.VECTOR || strategy == EnterpriseRetrievalStrategy.HYBRID
                || strategy == EnterpriseRetrievalStrategy.HYBRID_RERANK) {
            long started = System.nanoTime();
            try {
                vectorHits = repository.searchVector(embeddingModel.embed(safeQuery), access, vectorTopK,
                        similarityThreshold);
            } catch (Exception e) {
                log.warn("Enterprise vector retrieval failed; continuing with keyword fallback: {}", e.getMessage());
            } finally {
                vectorMs = elapsedMs(started);
            }
        }

        if (strategy == EnterpriseRetrievalStrategy.KEYWORD || strategy == EnterpriseRetrievalStrategy.HYBRID
                || strategy == EnterpriseRetrievalStrategy.HYBRID_RERANK) {
            long started = System.nanoTime();
            try {
                keywordHits = repository.searchKeyword(safeQuery, access, keywordTopK);
            } catch (Exception e) {
                log.warn("Enterprise FTS retrieval failed; continuing with vector fallback: {}", e.getMessage());
            } finally {
                ftsMs = elapsedMs(started);
            }
        }

        long rrfStarted = System.nanoTime();
        List<EnterpriseSearchHit> candidates;
        if (strategy == EnterpriseRetrievalStrategy.VECTOR) candidates = vectorHits;
        else if (strategy == EnterpriseRetrievalStrategy.KEYWORD) candidates = keywordHits;
        else candidates = rrfFusion.fuse(vectorHits, keywordHits, rrfK, Math.max(finalTopK * 3, finalTopK));
        long rrfMs = elapsedMs(rrfStarted);

        long rerankStarted = System.nanoTime();
        if (strategy == EnterpriseRetrievalStrategy.HYBRID_RERANK) {
            try {
                candidates = reranker.rerank(safeQuery, candidates);
            } catch (Exception e) {
                log.warn("Enterprise reranker failed; using RRF result: {}", e.getMessage());
            }
        }
        long rerankMs = strategy == EnterpriseRetrievalStrategy.HYBRID_RERANK ? elapsedMs(rerankStarted) : 0;

        List<EnterpriseSearchHit> finalHits = limitContext(candidates);
        return new EnterpriseRetrievalResult(finalHits, strategy,
                new EnterpriseRetrievalMetrics(vectorMs, ftsMs, rrfMs, rerankMs,
                        distinctCount(vectorHits, keywordHits), finalHits.size()));
    }

    private List<EnterpriseSearchHit> limitContext(List<EnterpriseSearchHit> candidates) {
        List<EnterpriseSearchHit> result = new ArrayList<>();
        int chars = 0;
        for (EnterpriseSearchHit candidate : candidates) {
            if (result.size() >= finalTopK || chars >= maxContextChars) break;
            int remaining = maxContextChars - chars;
            String content = candidate.content();
            if (content.length() > remaining) content = content.substring(0, remaining);
            if (content.isBlank()) continue;
            result.add(new EnterpriseSearchHit(candidate.chunkId(), candidate.documentId(), candidate.externalId(),
                    candidate.source(), candidate.sourceType(), candidate.title(), content, candidate.tenantId(),
                    candidate.department(), candidate.accessLevel(), candidate.chunkIndex(), candidate.score(),
                    result.size() + 1, candidate.metadata()));
            chars += content.length();
        }
        return List.copyOf(result);
    }

    private int distinctCount(List<EnterpriseSearchHit> vectorHits, List<EnterpriseSearchHit> keywordHits) {
        return (int) java.util.stream.Stream.concat(vectorHits.stream(), keywordHits.stream())
                .map(EnterpriseSearchHit::chunkId).distinct().count();
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
