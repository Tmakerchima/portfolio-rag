# Supabase → ParadeDB：Contextual BM25 部署

本目录只放 ParadeDB / logical replication 基础设施，不由 Spring 启动时自动执行生产 DDL。

最终职责边界：

```text
Supabase PostgreSQL = documents、chunks、ACL、corpus、pgvector 的 source of truth
ParadeDB            = enterprise_chunks.index_content 的 BM25 search replica
Application         = 读取 Supabase 向量 + 读取 ParadeDB lexical 结果，再做 RRF
```

BM25 文本字段必须是 `enterprise_chunks.index_content`。它由：

```text
contextual_prefix（仅 retrieval） + original content（唯一可引用证据）
```

组成；回答和 citation 仍只能使用 `content`。

## 本地验证

1. 在仓库根目录执行 `docker compose -f .\portfolio-rag\docker-compose.paradedb.yml up -d`。
2. 运行真实 fixture integration test（它会建表、建 `USING bm25` index、执行 `pdb.score()` 并清理 fixture）：

```powershell
$env:ENTERPRISE_BM25_INTEGRATION="true"
$env:ENTERPRISE_RAG_BM25_URL="jdbc:postgresql://localhost:5433/enterprise_search"
$env:ENTERPRISE_RAG_BM25_USERNAME="postgres"
$env:ENTERPRISE_RAG_BM25_PASSWORD="postgres"
cd portfolio-rag
mvn -Dtest=ParadeDbBm25IntegrationTest test
```

3. 生产同构 schema 准备好后，再按顺序执行 `03_paradedb_bm25_index.sql`、`04_smoke_test.sql`。
4. 设置 `ENTERPRISE_RAG_LEXICAL_BACKEND=PARADEDB_BM25` 和独立连接变量后启动应用。
5. 访问 `/api/enterprise/health`，应看到 `bm25: UP`；没有 BM25 时默认自动回到 `POSTGRES_FTS`。

本地没有 Docker 时必须记录 `SKIPPED: Docker unavailable`。GitHub Actions 的 `paradedb-integration` job 会显式设置 `ENTERPRISE_BM25_INTEGRATION=true` 并对真实 ParadeDB service container 执行同一个测试。

## 生产复制步骤

1. 在 Supabase Dashboard/SQL editor 检查项目是否允许 logical replication，并按 `01_supabase_publication.sql` 创建 publication。Supabase 的 replication slot、网络白名单和复制用户权限可能需要平台侧人工配置。
2. 在 ParadeDB 先建立同构表和扩展，再把 `02_paradedb_subscription.sql` 中的尖括号占位符替换为 secret manager 注入的连接信息；不要把真实密码提交到 Git。
3. 等待初始 copy 完成，确认 `enterprise_documents`、`enterprise_chunks`、`enterprise_corpora` 三张表的行数和 `index_content` 校验一致。
4. 在 ParadeDB 执行 `03_paradedb_bm25_index.sql`。当前索引使用 `pdb.icu`，兼顾中文、英文和技术标识符；`chunk_id` 使用 `pdb.literal` 作为唯一且不分词的 key field。
5. 使用 `04_smoke_test.sql` 验证 `pdb.score()`。仅验证通过后才把应用 backend 切到 `PARADEDB_BM25`。

## 连接变量

```text
ENTERPRISE_RAG_LEXICAL_BACKEND=POSTGRES_FTS|PARADEDB_BM25
ENTERPRISE_RAG_LEXICAL_FAIL_OPEN=true|false
ENTERPRISE_RAG_BM25_URL=jdbc:postgresql://...
ENTERPRISE_RAG_BM25_USERNAME=...
ENTERPRISE_RAG_BM25_PASSWORD=...
ENTERPRISE_RAG_BM25_TOP_K=20
ENTERPRISE_RAG_BM25_CONNECT_TIMEOUT_MS=1000
ENTERPRISE_RAG_BM25_QUERY_TIMEOUT_MS=3000
ENTERPRISE_RAG_BM25_MAX_RETRIES=0
ENTERPRISE_RAG_BM25_MAXIMUM_POOL_SIZE=4
ENTERPRISE_RAG_BM25_MINIMUM_IDLE=0
```

Spring 中的 `dataSource` / `primaryJdbcTemplate` 是显式 `@Primary` 的 Supabase Hikari pool；`bm25DataSource` / `bm25JdbcTemplate` 是带 qualifier 的独立 ParadeDB Hikari pool。普通 repository、ingestion、corpus 和 pgvector 不会因为启用 BM25 而改连搜索副本。

## 回滚

将 `ENTERPRISE_RAG_LEXICAL_BACKEND=POSTGRES_FTS`，重启应用即可回到既有 `search_vector + GIN + ts_rank_cd`。不要删除 ParadeDB index，也不要删除主库旧 FTS 结构；复制故障会通过 `POSTGRES_FTS_FALLBACK:<reason>` 记录。

## 官方语法依据

本项目固定 `paradedb/paradedb:0.24.3-pg16`，因此 DDL 使用该版本原生的 `USING bm25`。ParadeDB 0.25+ 官方文档将主名称改为 `USING paradedb`，同时明确保留 `USING bm25` 兼容别名；查询仍必须通过 `pdb.score()` 获得真实 BM25 score。部署前必须在目标固定镜像上执行 smoke test，因为跨版本升级可能要求重建索引。
