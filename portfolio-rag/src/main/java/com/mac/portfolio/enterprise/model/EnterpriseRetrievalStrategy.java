package com.mac.portfolio.enterprise.model;

import java.util.Locale;

public enum EnterpriseRetrievalStrategy {
    VECTOR,
    KEYWORD,
    HYBRID,
    HYBRID_RERANK;

    public static EnterpriseRetrievalStrategy parse(String value, EnterpriseRetrievalStrategy fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
