package com.mac.portfolio.enterprise.model;

/**
 * 结构化切块器产生的最终原始 Chunk。
 * content 必须保持为来源文档原文，回答和引用不能把模型生成的上下文前缀伪装成原文。
 */
public record EnterpriseChunk(
        String chunkId,
        int index,
        String content,
        String sectionPath,
        int tokenCount) {

    public EnterpriseChunk {
        // Record 的紧凑构造器负责统一清理输入并保护基本不变量。
        content = content == null ? "" : content.trim();
        sectionPath = sectionPath == null ? "" : sectionPath.trim();
        if (index < 0) throw new IllegalArgumentException("chunk index must not be negative");
        if (tokenCount < 0) throw new IllegalArgumentException("token count must not be negative");
    }

    /** 兼容小型单元测试：调用方没有 Tokenizer 时可以只传三个核心字段。 */
    public EnterpriseChunk(String chunkId, int index, String content) {
        this(chunkId, index, content, "", 0);
    }
}
