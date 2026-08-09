package com.mac.portfolio.enterprise.controller;

import com.mac.portfolio.enterprise.ingestion.EnterpriseIngestionService;
import com.mac.portfolio.enterprise.model.EnterpriseDocumentInput;
import com.mac.portfolio.enterprise.service.EnterpriseCorpusService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/enterprise/admin")
public class EnterpriseAdminController {

    private final EnterpriseIngestionService ingestionService;
    private final EnterpriseCorpusService corpusService;
    private final String adminToken;

    public EnterpriseAdminController(EnterpriseIngestionService ingestionService,
                                     EnterpriseCorpusService corpusService,
                                     @Value("${enterprise.rag.admin-token:}") String adminToken) {
        this.ingestionService = ingestionService;
        this.corpusService = corpusService;
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

    public record CorpusRequest(String datasetName, String datasetVersion, Long expectedDocuments,
                                 String embeddingProvider, String embeddingModel, Integer dimension,
                                 String chunkerVersion) {}

    @PostMapping("/corpora")
    public ResponseEntity<?> createCorpus(
            @RequestHeader(name = "X-Enterprise-Admin-Token", required = false) String requestToken,
            @RequestBody CorpusRequest request) {
        if (!authorized(requestToken)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized");
        if (request == null || request.datasetName() == null || request.datasetVersion() == null) {
            return ResponseEntity.badRequest().body("datasetName and datasetVersion are required");
        }
        UUID corpusId = corpusService.create(request.datasetName(), request.datasetVersion(),
                request.expectedDocuments() == null ? 0 : request.expectedDocuments(),
                request.embeddingProvider(), request.embeddingModel(),
                request.dimension() == null ? 1024 : request.dimension(), request.chunkerVersion());
        return ResponseEntity.ok(Map.of("corpus_id", corpusId, "state", "STAGING"));
    }

    @PostMapping("/corpora/{corpusId}/activate")
    public ResponseEntity<?> activate(
            @RequestHeader(name = "X-Enterprise-Admin-Token", required = false) String requestToken,
            @PathVariable UUID corpusId) {
        if (!authorized(requestToken)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized");
        corpusService.activate(corpusId);
        return ResponseEntity.ok(Map.of("corpus_id", corpusId, "state", "ACTIVE"));
    }

    @PostMapping("/corpora/{corpusId}/rollback")
    public ResponseEntity<?> rollback(
            @RequestHeader(name = "X-Enterprise-Admin-Token", required = false) String requestToken,
            @PathVariable UUID corpusId) {
        if (!authorized(requestToken)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Unauthorized");
        corpusService.rollback(corpusId);
        return ResponseEntity.ok(Map.of("corpus_id", corpusId, "state", "ACTIVE"));
    }

    private boolean authorized(String requestToken) {
        return !adminToken.isBlank() && requestToken != null && adminToken.equals(requestToken);
    }
}
