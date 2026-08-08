package com.mac.portfolio.enterprise.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/enterprise")
public class EnterpriseHealthController {

    private final JdbcTemplate jdbcTemplate;

    public EnterpriseHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            Long documents = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM enterprise_documents WHERE deleted_at IS NULL", Long.class);
            Long chunks = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM enterprise_chunks", Long.class);
            body.put("status", documents != null && documents > 0 ? "READY" : "EMPTY");
            body.put("documents", documents == null ? 0 : documents);
            body.put("chunks", chunks == null ? 0 : chunks);
            body.put("message", documents != null && documents > 0
                    ? "Enterprise corpus is indexed"
                    : "Enterprise schema exists but no corpus has been ingested");
            return ResponseEntity.ok(body);
        } catch (DataAccessException error) {
            body.put("status", "MIGRATION_REQUIRED");
            body.put("documents", 0);
            body.put("chunks", 0);
            body.put("message", "Apply V1__enterprise_rag.sql before ingesting enterprise documents");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}
