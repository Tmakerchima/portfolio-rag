# EnterpriseRAG 部署手册

## Supabase / PostgreSQL

1. 在目标数据库人工审阅并依次执行 `V1__enterprise_rag.sql`、`V2__enterprise_rag_generations.sql`、`V3__enterprise_contextual_retrieval.sql`。
2. 确认 `vector` 扩展可用、`enterprise_documents` 和 `enterprise_chunks` 存在，以及 HNSW/GIN 索引已建立。
3. 本任务不会自动连接生产 Supabase，也不会修改或删除现有 `vector_store`。

### 语料文件在哪里

仓库不携带 EnterpriseRAG-Bench 的完整语料，也不会在应用启动时自动下载或生成企业文档。当前本机已下载官方问题集和 GitHub source slice；完整语料包仍需断点续传，未完成文件明确保存为 `all_documents.zip.partial`，不可用于导入。下载后的源文件放在本地运行导入脚本的目录，例如：

```text
eval/data/EnterpriseRAG-Bench/all_documents.zip
eval/data/EnterpriseRAG-Bench/questions.jsonl
eval/data/EnterpriseRAG-Bench/github_slice_0001.zip
eval/data/EnterpriseRAG-Bench/<source-type>/*.txt
```

`eval/data/` 被 `.gitignore` 忽略，避免把数百 MB/GB 的数据集提交进 Git。小型 canary 可以调用 `/api/enterprise/admin/ingest`；长时间任务使用 `eval/enterprise_rag_worker.py`，由受控 runner 流式读取、写 durable checkpoint，并将数据写入新的 staging corpus。

完整包可使用官方 release 地址断点续传：

```powershell
curl.exe -L --retry 5 --retry-delay 5 --continue-at - --fail --show-error `
  -o .\eval\data\EnterpriseRAG-Bench\all_documents.zip `
  https://github.com/onyx-dot-app/EnterpriseRAG-Bench/releases/download/v1.0.0/all_documents.zip
```

如果 Supabase Table Editor 搜索不到 `enterprise_documents` / `enterprise_chunks`，说明上面的 migration 尚未执行；如果表存在但行数为 0，说明 migration 已执行但导入命令尚未运行。可以先查看 `GET /api/enterprise/health`，它会分别返回 `MIGRATION_REQUIRED`、`EMPTY` 或 `READY`。

在发送任何请求前，可以用导入器的 `--dry-run` 验证压缩包可读、source type 和文档数量；它不会调用后端。当前已验证 GitHub slice 可以读取 5,000 篇文档，并包含 multipart upload limits 的目标文档。

## Shared Spring Boot backend

在 Railway/Render 的同一个后端服务中保留原有变量，并新增：

```text
ENTERPRISE_RAG_ENABLED=true
ENTERPRISE_RAG_MAX_DOCUMENTS=1000 # HTTP canary guard only
ENTERPRISE_RAG_MAX_CHUNK_TOKENS=700
ENTERPRISE_RAG_CHUNK_OVERLAP_TOKENS=80
ENTERPRISE_RAG_VECTOR_BACKEND=PGVECTOR
ENTERPRISE_RAG_ACTIVE_CORPUS_ID=<only-a-validated-active-corpus>
ENTERPRISE_RAG_EMBEDDING_MODEL=text-embedding-v3
ENTERPRISE_RAG_EMBEDDING_DIMENSIONS=1024
ENTERPRISE_RAG_VECTOR_TOP_K=12
ENTERPRISE_RAG_KEYWORD_TOP_K=12
ENTERPRISE_RAG_FINAL_TOP_K=5
ENTERPRISE_RAG_RRF_K=60
ENTERPRISE_RAG_STRATEGY=HYBRID
ENTERPRISE_RAG_RERANK_ENABLED=true
ENTERPRISE_RAG_RERANKER_MODE=HEURISTIC # LLM is explicit and billable
ENTERPRISE_RAG_CONTEXTUAL_ENABLED=false # explicit opt-in, one chat call/chunk
ENTERPRISE_RAG_AGENTIC_ENABLED=false # optional second-pass query planner
ENTERPRISE_RAG_ADMIN_TOKEN=<独立的长随机 token>
```

`ENTERPRISE_RAG_ADMIN_TOKEN` 只用于小型管理操作；不要提交或放进前端。未配置 token 时受保护 admin endpoint 会返回 403。EnterpriseRAG 不会在 JVM 启动阶段自动 embedding。

## Enterprise frontend / Vercel

1. 将 Vercel Root Directory 设置为 `enterprise-rag-frontend`。
2. 设置 `VITE_API_BASE_URL` 为共享 Spring Boot 后端地址。
3. 部署成功后，才在 Portfolio 前端构建环境设置 `VITE_ENTERPRISE_RAG_LIVE_URL`；未设置时卡片只显示 GitHub。

## Import commands

从官方 release 下载 `all_documents.zip` 后，在本地或受控 runner 运行：

```bash
python -m pip install -r eval/requirements.txt
python eval/enterprise_rag_worker.py --archive ./data/all_documents.zip --max-documents 1000 --dry-run
python eval/enterprise_rag_worker.py --archive ./data/all_documents.zip --max-documents 1000 --chunk-tokens 700 --overlap-tokens 80 --database-url "$ENTERPRISE_DATABASE_URL" --embedding-api-key "$DASHSCOPE_API_KEY"
```

需要 Anthropic-style LLM prefix 时额外传入 `--contextual-enabled --contextual-model qwen-plus`；这会为每个 chunk 增加一次 chat 请求，必须先在小样本评测成本和召回收益。worker 会拒绝 `.partial`/`.range*` 和损坏压缩包；请先 dry-run，再从 1000 开始观察 embedding、contextualization 成本、WAL 和数据库容量。

`structure-token-contextual-v2` 会把 pipeline fingerprint 写进 checkpoint。旧 `worker-v1` checkpoint 不能使用 `--resume` 写入新 corpus；切换 chunk、overlap 或 contextual model 时必须新建 checkpoint 和 STAGING corpus。
