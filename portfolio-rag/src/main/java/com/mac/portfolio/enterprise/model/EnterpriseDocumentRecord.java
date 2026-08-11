package com.mac.portfolio.enterprise.model;

/**
 * 数据库中已有文档的轻量快照，用于判断本次请求应跳过、重建索引还是创建新文档。
 */
public record EnterpriseDocumentRecord(
        String documentId,
        String externalId,
        String source,
        String contentHash,
        String indexFingerprint,
        int version,
        boolean deleted) {

    /** 兼容尚未保存 indexFingerprint 的旧测试数据和旧调用方。 */
    public EnterpriseDocumentRecord(String documentId, String externalId, String source,
                                    String contentHash, int version, boolean deleted) {
        this(documentId, externalId, source, contentHash, "legacy-v1", version, deleted);
    }
}
