package com.mac.portfolio.enterprise.model;

import java.util.Map;

public record EnterpriseSearchHit(
        String chunkId,
        String documentId,
        String externalId,
        String source,
        String sourceType,
        String title,
        String content,
        String tenantId,
        String department,
        String accessLevel,
        int chunkIndex,
        double score,
        int rank,
        Map<String, Object> metadata) {

    public EnterpriseSearchHit withScore(double newScore, int newRank) {
        return new EnterpriseSearchHit(chunkId, documentId, externalId, source, sourceType, title, content,
                tenantId, department, accessLevel, chunkIndex, newScore, newRank, metadata);
    }
}
