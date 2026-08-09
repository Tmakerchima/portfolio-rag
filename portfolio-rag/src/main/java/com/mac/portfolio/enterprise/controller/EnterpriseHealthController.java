package com.mac.portfolio.enterprise.controller;

import com.mac.portfolio.enterprise.service.EnterpriseCorpusService;
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

    private final EnterpriseCorpusService corpusService;

    public EnterpriseHealthController(EnterpriseCorpusService corpusService) {
        this.corpusService = corpusService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            body.putAll(corpusService.stats());
            body.put("documents", body.getOrDefault("document_count", 0L));
            body.put("chunks", body.getOrDefault("chunk_count", 0L));
            body.putIfAbsent("message", "Enterprise corpus status is available");
            return ResponseEntity.ok(body);
        } catch (DataAccessException error) {
            body.put("status", "MIGRATION_REQUIRED");
            body.put("documents", 0);
            body.put("chunks", 0);
            body.put("message", "Apply V1__enterprise_rag.sql and V2__enterprise_rag_generations.sql before ingesting enterprise documents");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        try {
            return ResponseEntity.ok(corpusService.stats());
        } catch (DataAccessException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "MIGRATION_REQUIRED", "message", "Enterprise corpus migrations are not applied"));
        }
    }
}
