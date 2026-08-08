package com.mac.portfolio.enterprise.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.portfolio.enterprise.controller.EnterpriseChatController.ChatRequest;
import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;
import com.mac.portfolio.enterprise.model.EnterpriseRetrievalStrategy;
import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;
import com.mac.portfolio.enterprise.retrieval.EnterpriseRetrievalMetrics;
import com.mac.portfolio.enterprise.retrieval.EnterpriseRetrievalResult;
import com.mac.portfolio.enterprise.retrieval.EnterpriseRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EnterpriseChatService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseChatService.class);
    private static final String SOURCES_MARKER = "@@SOURCES@@";
    private static final String METRICS_MARKER = "@@METRICS@@";
    private static final String ERROR_MARKER = "@@ERROR@@";
    private static final String SYSTEM_PROMPT = """
            You are Enterprise Knowledge Assistant. Answer only from the retrieved enterprise context.
            If the context does not contain enough evidence, say that there is insufficient evidence and
            explain what is missing. Never invent company facts, permissions, people, dates, or metrics.
            Keep answers concise and cite the relevant source title or document when possible.
            """;

    private final ChatClient chatClient;
    private final EnterpriseRetrievalService retrievalService;
    private final ObjectMapper objectMapper;
    private final EnterpriseRetrievalStrategy defaultStrategy;
    private final boolean enabled;

    public EnterpriseChatService(ChatClient chatClient,
                                 EnterpriseRetrievalService retrievalService,
                                 ObjectMapper objectMapper,
                                 @Value("${enterprise.rag.strategy:HYBRID}") String defaultStrategy,
                                 @Value("${enterprise.rag.enabled:true}") boolean enabled) {
        this.chatClient = chatClient;
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
        this.defaultStrategy = EnterpriseRetrievalStrategy.parse(defaultStrategy, EnterpriseRetrievalStrategy.HYBRID);
        this.enabled = enabled;
    }

    public Flux<String> streamAnswer(ChatRequest request) {
        return Flux.defer(() -> {
            String requestId = UUID.randomUUID().toString();
            long started = System.nanoTime();
            if (!enabled) return Flux.just(errorFrame(requestId, "EnterpriseRAG is disabled"));
            if (request == null) return Flux.just(errorFrame(requestId, "request must not be null"));

            String question = request.question() == null ? "" : request.question().trim();
            if (question.isBlank()) return Flux.just(errorFrame(requestId, "question must not be blank"));
            EnterpriseAccessContext access = EnterpriseAccessContext.from(
                    request.role(), request.tenantId());
            EnterpriseRetrievalStrategy strategy = EnterpriseRetrievalStrategy.parse(request.strategy(), defaultStrategy);
            EnterpriseRetrievalResult retrieval = retrievalService.retrieve(question, access, strategy);
            String sourcesFrame = sourcesFrame(requestId, retrieval);
            String groundedPrompt = groundedPrompt(question, access, retrieval.hits());

            Flux<String> answer = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(groundedPrompt)
                    .stream()
                    .content()
                    .onErrorResume(error -> Flux.just(errorFrame(requestId, "LLM request failed")));
            Mono<String> metrics = Mono.fromSupplier(() -> metricsFrame(requestId, retrieval, started));
            return Flux.concat(Mono.just(sourcesFrame), answer, metrics)
                    .doOnComplete(() -> log.info(
                            "enterprise_request request_id={} strategy={} vector_latency={}ms fts_latency={}ms " +
                                    "rerank_latency={}ms candidate_count={} context_count={} total_latency={}ms",
                            requestId, strategy, retrieval.metrics().vectorMs(), retrieval.metrics().ftsMs(),
                            retrieval.metrics().rerankMs(), retrieval.metrics().candidateCount(),
                            retrieval.metrics().finalContextCount(), elapsedMs(started)));
        }).onErrorResume(error -> Flux.just(errorFrame("unknown", "Enterprise request failed")))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String groundedPrompt(String question, EnterpriseAccessContext access, List<EnterpriseSearchHit> hits) {
        String context = hits.isEmpty() ? "INSUFFICIENT EVIDENCE: no authorized documents were retrieved."
                : hits.stream().map(hit -> "[" + hit.sourceType() + " | " + hit.title() + " | " + hit.chunkId() + "]\n"
                        + hit.content()).reduce((left, right) -> left + "\n\n" + right).orElse("");
        return """
                Authorization role: %s
                Retrieved context:
                <context>
                %s
                </context>

                User question: %s
                """.formatted(access.role(), context, question);
    }

    private String sourcesFrame(String requestId, EnterpriseRetrievalResult retrieval) {
        List<Map<String, Object>> sources = retrieval.hits().stream().map(hit -> {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("source_type", hit.sourceType());
            source.put("source", hit.source());
            source.put("title", hit.title());
            source.put("document_id", hit.externalId());
            source.put("chunk_id", hit.chunkId());
            source.put("chunk", snippet(hit.content()));
            source.put("rank", hit.rank());
            source.put("score", round(hit.score()));
            return source;
        }).toList();
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("request_id", requestId);
        frame.put("strategy", retrieval.strategy().name());
        frame.put("sources", sources);
        return jsonFrame(SOURCES_MARKER, frame);
    }

    private String metricsFrame(String requestId, EnterpriseRetrievalResult retrieval, long started) {
        EnterpriseRetrievalMetrics values = retrieval.metrics();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("request_id", requestId);
        metrics.put("strategy", retrieval.strategy().name());
        metrics.put("vector_ms", values.vectorMs());
        metrics.put("fts_ms", values.ftsMs());
        metrics.put("rrf_ms", values.rrfMs());
        metrics.put("rerank_ms", values.rerankMs());
        metrics.put("llm_ms", Math.max(0, elapsedMs(started) - values.vectorMs() - values.ftsMs() - values.rrfMs() - values.rerankMs()));
        metrics.put("total_ms", elapsedMs(started));
        metrics.put("candidate_count", values.candidateCount());
        metrics.put("final_context_count", values.finalContextCount());
        return jsonFrame(METRICS_MARKER, metrics);
    }

    private String errorFrame(String requestId, String message) {
        return jsonFrame(ERROR_MARKER, Map.of("request_id", requestId, "message", message));
    }

    private String jsonFrame(String marker, Object value) {
        try {
            return marker + objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return marker + "{}";
        }
    }

    private String snippet(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 180 ? oneLine.substring(0, 180) + "…" : oneLine;
    }

    private double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
