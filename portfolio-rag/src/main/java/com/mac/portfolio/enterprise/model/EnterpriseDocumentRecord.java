package com.mac.portfolio.enterprise.model;

public record EnterpriseDocumentRecord(
        String documentId,
        String externalId,
        String source,
        String contentHash,
        int version,
        boolean deleted) {
}
