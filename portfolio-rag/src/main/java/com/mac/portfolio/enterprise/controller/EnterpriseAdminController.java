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

/**
 * Enterprise RAG 的管理接口入口。
 *
 * <p>Spring Boot 启动时只会创建这个 Controller 并注册路由，不会自动调用 {@code /ingest}。
 * 只有外部客户端主动发送 HTTP POST 请求时，才会开始文档入库。</p>
 */
@RestController
@RequestMapping("/api/enterprise/admin")
public class EnterpriseAdminController {

    private final EnterpriseIngestionService ingestionService;
    private final EnterpriseCorpusService corpusService;
    /** 服务端保存的入库共享密钥；为空时表示主动关闭所有管理接口。 */
    private final String adminToken;

    public EnterpriseAdminController(EnterpriseIngestionService ingestionService,
                                     EnterpriseCorpusService corpusService,
                                     // 读取 Spring 配置 enterprise.rag.admin-token；未配置时默认是空字符串。
                                     @Value("${enterprise.rag.admin-token:}") String adminToken) {
        this.ingestionService = ingestionService;
        this.corpusService = corpusService;
        this.adminToken = adminToken == null ? "" : adminToken;
    }

    /** 把请求 JSON 中的 documents 数组映射为 Java 文档列表。 */
    public record IngestRequest(List<EnterpriseDocumentInput> documents) {}

    /**
     * 手动批量入库接口：POST /api/enterprise/admin/ingest。
     * 调用方必须同时提交管理请求头和 documents JSON；该方法不会在应用启动时执行。
     */
    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(
            @RequestHeader(name = "X-Enterprise-Admin-Token", required = false) String requestToken,
            @RequestBody IngestRequest request) {
        // 服务端密钥为空等于“接口关闭”；请求头缺失或密钥不相等也统一返回 403。
        if (adminToken.isBlank() || requestToken == null || !adminToken.equals(requestToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Enterprise ingestion is disabled or unauthorized");
        }
        // 权限通过后再验证业务请求，避免把无效请求传给入库服务。
        if (request == null || request.documents() == null) {
            return ResponseEntity.badRequest().body("documents must not be null");
        }
        // 真正的标准化、切块、Embedding 和数据库写入从这里开始。
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
        // corpora 创建、激活和回滚接口与 /ingest 共用同一把管理密钥。
        return !adminToken.isBlank() && requestToken != null && adminToken.equals(requestToken);
    }
}
