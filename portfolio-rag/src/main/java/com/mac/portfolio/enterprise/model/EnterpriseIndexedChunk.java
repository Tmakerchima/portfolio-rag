package com.mac.portfolio.enterprise.model;

/**
 * 将“来源证据”与“只用于检索的增强文本”分开保存。
 *
 * <p>chunk.content 是可引用原文；contextualPrefix 是模型生成的定位前缀；
 * indexContent 通常是“前缀 + 原文”，只用于 Embedding 和全文索引。</p>
 */
public record EnterpriseIndexedChunk(
        EnterpriseChunk chunk,
        String contextualPrefix,
        String indexContent,
        int indexTokenCount) {

    public EnterpriseIndexedChunk {
        // 索引对象必须关联一个真实来源 Chunk，且最终索引文本不能为空。
        if (chunk == null) throw new IllegalArgumentException("chunk must not be null");
        contextualPrefix = contextualPrefix == null ? "" : contextualPrefix.trim();
        indexContent = indexContent == null ? "" : indexContent.trim();
        if (indexContent.isBlank()) throw new IllegalArgumentException("index content must not be blank");
        if (indexTokenCount < 1) throw new IllegalArgumentException("index token count must be positive");
    }
}
