package com.mac.portfolio.enterprise.model;

import java.util.Locale;

/** Demo authorization context used to demonstrate retrieval-time ACL filtering. */
public record EnterpriseAccessContext(String role, String tenantId, String department) {

    public EnterpriseAccessContext {
        role = normalizeRole(role);
        tenantId = normalizeNullable(tenantId);
        department = normalizeNullable(department);
    }

    public static EnterpriseAccessContext from(String requestedRole, String requestedTenantId) {
        String role = normalizeRole(requestedRole);
        String department = switch (role) {
            case "engineering", "finance", "hr" -> role;
            default -> null;
        };
        return new EnterpriseAccessContext(role, normalizeNullable(requestedTenantId), department);
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    private static String normalizeRole(String value) {
        String role = value == null ? "public" : value.trim().toLowerCase(Locale.ROOT);
        return switch (role) {
            case "public", "engineering", "finance", "hr", "admin" -> role;
            default -> "public";
        };
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
