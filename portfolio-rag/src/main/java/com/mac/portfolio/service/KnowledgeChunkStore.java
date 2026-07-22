package com.mac.portfolio.service;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class KnowledgeChunkStore {

    private final AtomicReference<List<Document>> chunks = new AtomicReference<>(List.of());

    public void replace(List<Document> newChunks) {
        chunks.set(List.copyOf(newChunks));
    }

    public List<Document> snapshot() {
        return chunks.get();
    }
}
