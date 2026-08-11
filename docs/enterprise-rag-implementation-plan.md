# EnterpriseRAG 实施计划

## 现有架构

- `portfolio-rag` 是唯一 Spring Boot 后端，`/api/chat` 负责简历 Agent 的 SSE 流式问答。
- Resume 由 `ResumeContextProvider` 读取完整 `about-mac.md`；当前不执行向量入库或在线检索。
- `portfolio-frontend` 是现有 Portfolio Vue 3 应用，Portfolio 与 EnterpriseRAG 共用后端但使用独立前端入口。

## 可复用组件与边界

- 复用现有 DashScope `EmbeddingModel`、`ChatClient`、WebFlux/CORS 与 PostgreSQL 数据源。
- EnterpriseRAG 放在 `com.mac.portfolio.enterprise` 下，使用独立表 `enterprise_documents` / `enterprise_chunks`；不改变 Resume 表、服务和 `/api/chat` 协议。
- Enterprise 检索使用 JDBC 直接执行 PostgreSQL FTS 与 pgvector 查询，确保 ACL 在数据库检索阶段生效。

## 可扩展性问题

- Enterprise 数据不能在 Java heap 中扫描，也不能复用 Resume 启动时清空表的入库策略。
- 增量入库需要内容哈希、版本、软删除、幂等 upsert 和单文档事务。

## 数据库、入库与检索设计

- 新增 additive migration：文档元数据、chunk、`vector(1024)`、`tsvector`/GIN、ACL 索引。
- 入库流程为 normalize → SHA-256 → unchanged skip / changed re-index → Token/结构感知 chunk → 可选 LLM contextual prefix → batched embedding → transaction 写入。
- `content` 保存可引用原文；`contextual_prefix` 是生成的检索辅助文本；`index_content` 才用于 embedding 与 FTS。
- 查询支持 VECTOR、KEYWORD、HYBRID、HYBRID_RERANK；向量和 FTS 结果用 RRF 融合，reranker 默认 HEURISTIC、可显式切到 LLM，失败回退 RRF。
- 可选 query planner 仅生成二次查询文本；每次二次检索复用相同的后端 ACL context，不能修改 role/tenant/department。
- `tenant_id`、`department`、`access_level` 在 SQL where 条件中应用，demo role 支持 public / engineering / finance / hr / admin。

## API 与前端

- 新增受保护的 `POST /api/enterprise/admin/ingest`（token 未配置时拒绝）和独立的 `POST /api/enterprise/chat` SSE 接口。
- Enterprise SSE 使用 `@@SOURCES@@`、正文、`@@METRICS@@`、`@@ERROR@@` 专属帧；Resume 只使用正文和 `@@TOOLS@@`。
- 新增 `enterprise-rag-frontend`，通过 `VITE_API_BASE_URL` 配置后端地址，展示角色、策略、回答、来源与延迟指标。

## 数据集与评估

- `eval/import_enterprise_bench.py` 按官方 `.txt` 导出格式读取，支持 1000/5000/10000/50000 文档上限和多 source type 采样，不提交数据文件。
- `eval/run_eval.py` 调用 Enterprise API 收集回答和来源；`retrieval_eval.py` 计算真实 ground truth 可支持的 HitRate、Recall、Precision、MRR、nDCG，无法测量时明确标记 `unsupported`。
- RAGAS 作为可选离线依赖，不进入线上请求；未安装 provider 或未执行时记录 `NOT_EXECUTED`。

## 迁移策略

- 只提交可人工审阅的 migration 与部署文档，不自动连接或修改生产 Supabase。
- Enterprise API 默认不执行入库，启动时也不导入 Enterprise 数据；生产部署需要先人工 apply migration，再配置 admin token 和离线导入命令。
