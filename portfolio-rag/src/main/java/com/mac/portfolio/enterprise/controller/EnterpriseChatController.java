package com.mac.portfolio.enterprise.controller;

import com.mac.portfolio.enterprise.service.EnterpriseChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/enterprise/chat")
public class EnterpriseChatController {

    private final EnterpriseChatService chatService;

    public EnterpriseChatController(EnterpriseChatService chatService) {
        this.chatService = chatService;
    }

    public record ChatRequest(String question, String role, String tenantId, String strategy) {}

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
        return chatService.streamAnswer(request);
    }
}
