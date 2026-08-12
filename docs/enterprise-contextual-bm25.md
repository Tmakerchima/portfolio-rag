# EnterpriseRAG：Contextual Embeddings + True ParadeDB BM25

这次升级把 lexical 检索从“写死的 PostgreSQL FTS”抽象成可切换 backend：默认仍是 `POSTGRES_FTS`，配置 `PARADEDB_BM25` 后才会使用 ParadeDB 的真实 BM25 index。没有 benchmark 数据时，文档不宣称 Recall 提升；请用 `eval/` 的 evaluator 生成真实结果。

## 最终数据流

```text
Document
  ↓
Structure-aware Chunker
  ↓
content（原始可引用 chunk）
  + contextual_prefix（LLM 生成、仅检索）
  ↓
index_content = contextual_prefix + content
  ├─ EmbeddingModel.embed(index_content) → Supabase pgvector
  └─ ParadeDB BM25(index_content) → lexical candidates
       ↓
  Vector + BM25 → RRF → optional Reranker → authorized evidence → Qwen
```

`content`、`contextual_prefix`、`index_content` 三个字段不能混用：SSE source citation 和最终 grounded prompt 只能读取 `content`；embedding 与 BM25 必须读取同一个 `index_content`。例如原文只有 `The revenue increased by 3%.`，上下文可以补充 `ACME Corporation Q2 2025 financial report`，但补充文字不能作为原始证据展示。

## 为什么是“真正 BM25”

`PostgresFtsLexicalRetriever` 明确保留旧的 `search_vector + ts_rank_cd`，只作为 fallback。`ParadeDbBm25LexicalRetriever` 使用独立 `bm25JdbcTemplate`，SQL 同时包含：

```sql
WHERE c.index_content ||| ?
ORDER BY pdb.score(c.chunk_id) DESC, c.chunk_id ASC
LIMIT ?
```

这不是重命名 FTS：`|||` 触发 ParadeDB operator，`pdb.score(c.chunk_id)` 读取 ParadeDB index 的 BM25 score；该 SQL 不使用 `ts_rank_cd`。对应 index 在 `deploy/paradedb/03_paradedb_bm25_index.sql`。项目固定镜像 `0.24.3-pg16`，DDL 使用 `USING bm25`；0.25+ 官方文档主推 `USING paradedb`，但仍把 `USING bm25` 保留为兼容别名。key field `chunk_id` 使用 `pdb.literal`，检索字段使用 `pdb.icu`。ICU 是中文、英文和技术标识符的初始折中；目标环境可用真实语料再评估 `unicode`、`chinese_compatible` 或 `source_code`。

## 配置

```text
ENTERPRISE_RAG_LEXICAL_BACKEND=POSTGRES_FTS|PARADEDB_BM25
ENTERPRISE_RAG_LEXICAL_FAIL_OPEN=true|false
ENTERPRISE_RAG_BM25_URL=jdbc:postgresql://paradedb-host:5432/enterprise_search
ENTERPRISE_RAG_BM25_USERNAME=<secret-managed-user>
ENTERPRISE_RAG_BM25_PASSWORD=<secret-managed-password>
ENTERPRISE_RAG_BM25_TOP_K=20
ENTERPRISE_RAG_BM25_CONNECT_TIMEOUT_MS=1000
ENTERPRISE_RAG_BM25_QUERY_TIMEOUT_MS=3000
ENTERPRISE_RAG_BM25_MAX_RETRIES=0
ENTERPRISE_RAG_BM25_MAXIMUM_POOL_SIZE=4
ENTERPRISE_RAG_BM25_MINIMUM_IDLE=0
```

生产建议先设置 `ENTERPRISE_RAG_LEXICAL_FAIL_OPEN=true`：BM25 不可用时结果会标记 `POSTGRES_FTS_FALLBACK:BM25_*`，并继续走主库 FTS。严格环境可以设为 `false`，让配置/连接故障显式失败。快速回滚只需设置 `ENTERPRISE_RAG_LEXICAL_BACKEND=POSTGRES_FTS`，不用删除旧列或数据库索引。

## ACL 与 corpus 安全

ParadeDB SQL 与原有 vector/FTS 查询保持相同过滤：`deleted_at IS NULL`、`ACTIVE corpus`、admin/public/department 规则、tenant isolation。BM25 结果进入 RRF、reranker 或 LLM 前已经完成 ACL；不能先取高分再在 Java 中过滤。应用仍只写 Supabase，ParadeDB 通过 logical replication 接收 `enterprise_documents`、`enterprise_chunks`、`enterprise_corpora`，避免 dual-write 不一致。

DataSource 完全隔离：Supabase `dataSource` 与 `primaryJdbcTemplate` 显式标记 `@Primary`，所有普通 repository、ingestion、corpus 与 pgvector 默认连接它；ParadeDB 使用独立 `bm25DataSource` / `bm25JdbcTemplate` qualifier。两边都是独立 Hikari pool，启用 `PARADEDB_BM25` 不会替换 Spring 主 DataSource。

## 本地运行和复制

```powershell
cd portfolio-rag
docker compose -f docker-compose.paradedb.yml up -d
# 准备同构 enterprise_* schema 后：
psql "$env:PARADEDB_ADMIN_URL" -f ../deploy/paradedb/03_paradedb_bm25_index.sql
psql "$env:PARADEDB_ADMIN_URL" -f ../deploy/paradedb/04_smoke_test.sql
```

真实 Supabase publication、replication slot、复制用户权限依赖项目平台和网络策略，不能安全地由 Spring 启动自动创建；请按 `deploy/paradedb/01_supabase_publication.sql`、`02_paradedb_subscription.sql` 和该目录 README 人工审核执行。当前仓库没有生产复制已完成的假设。

## Observability / health

`EnterpriseRetrievalMetrics` 保留兼容的 `ftsMs` 字段，但它现在表示 lexical latency，并额外返回 `lexicalBackend`。Enterprise SSE metrics 和日志记录 vector/lexical/RRF/rerank latency、候选数、fallback reason。`GET /api/enterprise/health` 不暴露 secret，会区分：

```json
{
  "lexicalBackend": "PARADEDB_BM25",
  "configuredLexicalBackend": "PARADEDB_BM25",
  "bm25": "UP"
}
```

BM25 down 且 fail-open 时，`lexicalBackend` 为 `POSTGRES_FTS_FALLBACK`、`bm25` 为 `DOWN`。
Health probe 会依次验证 ParadeDB 连接、固定 index 是否存在，并实际执行一次 `index_content ||| ? + pdb.score(chunk_id)`；只有 index 名存在但 BM25 query 不能执行时仍然返回 DOWN，且不会暴露 URL、用户名或密码。

## 测试与 benchmark

- 单元测试验证 BM25 SQL 有 `|||`、`pdb.score`、`LIMIT` 且没有 `ts_rank_cd`。
- fallback 测试模拟 ParadeDB 连接/SQL 异常，分别覆盖 fail-open 和 fail-closed。
- contextual fixture 验证 query 中的 `ACME/Q2/2025` 只出现在 prefix 时仍由 `index_content` 召回，而 citation 仍只有原文。
- `.github/workflows/enterprise-bm25.yml` 会启动固定版本的真实 ParadeDB container，显式开启 `ParadeDbBm25IntegrationTest`；开发机没有 Docker 时该外部测试保持 skip，不能写成 PASS。
- `RrfFusionTest` 验证 vector 与 lexical 的 rank-based 融合，不比较两个 backend 的原始 score。
- `eval/retrieval_eval.py` 计算 Recall@K、MRR、nDCG、HitRate、Precision；`eval/run_eval.py` 可分别运行 `VECTOR`、`KEYWORD`、`HYBRID`、`HYBRID_RERANK`。没有外部 API key 或运行数据库时应记录 skip，不得填造数字。

建议的 ablation 顺序：Vector only、Postgres FTS only、ParadeDB BM25 only、Vector+FTS+RRF、Vector+BM25+RRF、Contextual Vector+Contextual BM25+RRF、再加 reranker。每次记录模型、chunk size/overlap、topK、RRF k、reranker、corpus version、p50/p95 latency。

## 回滚清单

1. 设置 `ENTERPRISE_RAG_LEXICAL_BACKEND=POSTGRES_FTS` 并重启服务。
2. 保留 ParadeDB subscription/index 供排障，不删除 Supabase `search_vector`、GIN、pgvector、corpus generation。
3. 若某个新 corpus generation 有问题，使用现有 corpus activate/rollback 流程切回上一代；BM25 只查询 ACTIVE corpus。
4. 修复复制或 BM25 index 后再单独切回 `PARADEDB_BM25`。

## 关键文件

```text
portfolio-rag/src/main/java/com/mac/portfolio/enterprise/retrieval/
  EnterpriseLexicalRetriever.java
  PostgresFtsLexicalRetriever.java
  ParadeDbBm25LexicalRetriever.java
  ConfigurableEnterpriseLexicalRetriever.java
portfolio-rag/src/main/java/com/mac/portfolio/enterprise/config/EnterpriseBm25DataSourceConfig.java
portfolio-rag/src/main/resources/db/migration/V4__enterprise_bm25_support.sql
deploy/paradedb/
```
