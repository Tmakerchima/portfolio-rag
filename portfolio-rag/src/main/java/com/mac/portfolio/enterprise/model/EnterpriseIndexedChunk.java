package com.mac.portfolio.enterprise.model;

/**
 * Separates source evidence from retrieval-only augmentation. The generated prefix is useful for
 * retrieval, but must never be presented as if it were part of the source document.
 */
public record EnterpriseIndexedChunk(
        EnterpriseChunk chunk,
        String contextualPrefix,
        String indexContent,
        int indexTokenCount) {

    public EnterpriseIndexedChunk {
        if (chunk == null) throw new IllegalArgumentException("chunk must not be null");
        contextualPrefix = contextualPrefix == null ? "" : contextualPrefix.trim();
        indexContent = indexContent == null ? "" : indexContent.trim();
        if (indexContent.isBlank()) throw new IllegalArgumentException("index content must not be blank");
        if (indexTokenCount < 1) throw new IllegalArgumentException("index token count must be positive");
    }
}
