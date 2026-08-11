package com.mac.portfolio.enterprise.model;

/** Original, citable document content produced by the structure-aware chunker. */
public record EnterpriseChunk(
        String chunkId,
        int index,
        String content,
        String sectionPath,
        int tokenCount) {

    public EnterpriseChunk {
        content = content == null ? "" : content.trim();
        sectionPath = sectionPath == null ? "" : sectionPath.trim();
        if (index < 0) throw new IllegalArgumentException("chunk index must not be negative");
        if (tokenCount < 0) throw new IllegalArgumentException("token count must not be negative");
    }

    /** Kept for small unit-test fixtures and callers that do not own a tokenizer. */
    public EnterpriseChunk(String chunkId, int index, String content) {
        this(chunkId, index, content, "", 0);
    }
}
