package com.mac.portfolio.enterprise.model;

public record EnterpriseDocumentRecord(
        String documentId,
        String externalId,
        String source,
        String contentHash,
        String indexFingerprint,
        int version,
        boolean deleted) {

    public EnterpriseDocumentRecord(String documentId, String externalId, String source,
                                    String contentHash, int version, boolean deleted) {
        this(documentId, externalId, source, contentHash, "legacy-v1", version, deleted);
    }
}
