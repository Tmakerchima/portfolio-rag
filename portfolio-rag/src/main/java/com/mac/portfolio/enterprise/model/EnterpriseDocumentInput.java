package com.mac.portfolio.enterprise.model;

import java.time.Instant;
import java.util.Map;

public record EnterpriseDocumentInput(
        String externalId,
        String source,
        String sourceType,
        String title,
        String content,
        String tenantId,
        String department,
        String accessLevel,
        Map<String, Object> metadata,
        Instant sourceUpdatedAt) {

    public EnterpriseDocumentInput {
        externalId = require(externalId, "externalId");
        source = require(source, "source");
        sourceType = require(sourceType, "sourceType");
        title = title == null ? "" : title.trim();
        content = content == null ? "" : content;
        tenantId = tenantId == null || tenantId.isBlank() ? "default" : tenantId.trim();
        department = department == null || department.isBlank() ? "engineering" : department.trim().toLowerCase();
        accessLevel = accessLevel == null || accessLevel.isBlank() ? "public" : accessLevel.trim().toLowerCase();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
