package com.mac.portfolio.controller;

import com.mac.portfolio.service.RagService;
import com.mac.portfolio.service.RecommendationProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RagService ragService;
    private final RecommendationProvider recommendationProvider;

    public ChatController(RagService ragService, RecommendationProvider recommendationProvider) {
        this.ragService = ragService;
        this.recommendationProvider = recommendationProvider;
    }

    record ChatRequest(String question) {}

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
        return ragService.streamAnswer(request.question());
    }

    @GetMapping(value = "/recommendations", produces = MediaType.APPLICATION_JSON_VALUE)
    public java.util.List<RecommendationProvider.Recommendation> recommendations() {
        return recommendationProvider.get();
    }
}
