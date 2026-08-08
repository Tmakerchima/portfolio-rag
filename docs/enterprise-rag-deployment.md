# EnterpriseRAG 部署手册

## Supabase / PostgreSQL

1. 在目标数据库人工审阅并执行 `portfolio-rag/src/main/resources/db/migration/V1__enterprise_rag.sql`。
2. 确认 `vector` 扩展可用、`enterprise_documents` 和 `enterprise_chunks` 存在，以及 HNSW/GIN 索引已建立。
3. 本任务不会自动连接生产 Supabase，也不会修改或删除现有 `vector_store`。

### 语料文件在哪里

仓库不携带 EnterpriseRAG-Bench 的完整语料，也不会在应用启动时自动下载或生成企业文档。下载后的源文件应放在本地运行导入脚本的目录，例如：

```text
eval/data/EnterpriseRAG-Bench/all_documents.zip
eval/data/EnterpriseRAG-Bench/<source-type>/*.txt
```

`eval/data/` 被 `.gitignore` 忽略，避免把数百 MB/GB 的数据集提交进 Git。脚本读取 `.txt` 源文件，调用 `/api/enterprise/admin/ingest`，由后端完成 normalize → chunk → embedding → PostgreSQL 写入。

如果 Supabase Table Editor 搜索不到 `enterprise_documents` / `enterprise_chunks`，说明上面的 migration 尚未执行；如果表存在但行数为 0，说明 migration 已执行但导入命令尚未运行。可以先查看 `GET /api/enterprise/health`，它会分别返回 `MIGRATION_REQUIRED`、`EMPTY` 或 `READY`。

## Shared Spring Boot backend

在 Railway/Render 的同一个后端服务中保留原有变量，并新增：

```text
ENTERPRISE_RAG_ENABLED=true
ENTERPRISE_RAG_MAX_DOCUMENTS=5000
ENTERPRISE_RAG_VECTOR_TOP_K=12
ENTERPRISE_RAG_KEYWORD_TOP_K=12
ENTERPRISE_RAG_FINAL_TOP_K=5
ENTERPRISE_RAG_RRF_K=60
ENTERPRISE_RAG_STRATEGY=HYBRID
ENTERPRISE_RAG_RERANK_ENABLED=true
ENTERPRISE_RAG_ADMIN_TOKEN=<独立的长随机 token>
```

`ENTERPRISE_RAG_ADMIN_TOKEN` 只用于离线导入；不要提交或放进前端。未配置 token 时 `/api/enterprise/admin/ingest` 会返回 403。EnterpriseRAG 不会在 JVM 启动阶段自动 embedding。

## Enterprise frontend / Vercel

1. 将 Vercel Root Directory 设置为 `enterprise-rag-frontend`。
2. 设置 `VITE_API_BASE_URL` 为共享 Spring Boot 后端地址。
3. 部署成功后，才在 Portfolio 前端构建环境设置 `VITE_ENTERPRISE_RAG_LIVE_URL`；未设置时卡片只显示 GitHub。

## Import commands

从官方 release 下载 `all_documents.zip` 后，在本地或受控 runner 运行：

```bash
python eval/import_enterprise_bench.py --archive ./data/all_documents.zip --max-documents 1000 --api-base https://your-backend.example.com --admin-token "$ENTERPRISE_RAG_ADMIN_TOKEN"
python eval/import_enterprise_bench.py --archive ./data/all_documents.zip --max-documents 5000 --api-base https://your-backend.example.com --admin-token "$ENTERPRISE_RAG_ADMIN_TOKEN"
python eval/import_enterprise_bench.py --archive ./data/all_documents.zip --max-documents 10000 --api-base https://your-backend.example.com --admin-token "$ENTERPRISE_RAG_ADMIN_TOKEN"
python eval/import_enterprise_bench.py --archive ./data/all_documents.zip --max-documents 50000 --api-base https://your-backend.example.com --admin-token "$ENTERPRISE_RAG_ADMIN_TOKEN"
```

适配器会轮询多个 source type，批量调用受保护 upsert 接口；请先从 1000 开始观察 embedding 成本与数据库容量。
