package com.mac.portfolio.enterprise.retrieval;

/** lexical 健康状态；activeBackend 可以是 FTS fallback，而 configuredBackend 仍保留原配置。 */
public record EnterpriseLexicalHealth(
        String configuredBackend,
        String activeBackend,
        boolean healthy,
        String reason) {
}
