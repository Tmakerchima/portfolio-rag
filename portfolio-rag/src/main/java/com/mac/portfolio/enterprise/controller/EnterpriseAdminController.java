package com.mac.portfolio.enterprise.controller;

import com.mac.portfolio.enterprise.ingestion.EnterpriseIngestionService;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/enterprise/admin")
public class EnterpriseAdminController {

    private final EnterpriseIngestionService ingestionService;
    private final String adminToken;

    public EnterpriseAdminController(EnterpriseIngestionService ingestionService,
                                     @Value("${enterprise.rag.admin-token:}") String adminToken) {
        this.ingestionService = ingestionService;
        this.adminToken = adminToken == null ? "" : adminToken;
    }

    public record IngestRequest(List<EnterpriseDocumentInput> documents) {}

    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(
            @RequestHeader(name = "X-Enterprise-Admin-Token", required = false) String requestToken,
            @RequestBody IngestRequest request) {
        if (adminToken.isBlank() || requestToken == null || !adminToken.equals(requestToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Enterprise ingestion is disabled or unauthorized");
        }
        if (request == null || request.documents() == null) {
            return ResponseEntity.badRequest().body("documents must not be null");
        }
        return ResponseEntity.ok(ingestionService.ingestBatch(request.documents()));
    }
}
