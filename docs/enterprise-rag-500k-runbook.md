# EnterpriseRAG 500K runbook

All commands use environment variables. Never put a token, password or full database URL in Git, logs or the frontend.

## Preflight

```powershell
git status -sb
git fetch origin
python .\eval\enterprise_rag_worker.py `
  --archive .\eval\data\EnterpriseRAG-Bench\all_documents.zip `
  --max-documents 1000 --dry-run `
  --manifest .\eval\data\EnterpriseRAG-Bench\canary-manifest.json
```

先运行 `python -m pip install -r .\eval\requirements.txt`；token-aware worker 使用 `tiktoken`。

The worker rejects `.partial` and `.range*` files and corrupt ZIP members. A successful dry-run produces document/source counts and a manifest without calling the embedding provider or database.

## Database migrations

Apply and review V1, V2 and V3 with a privileged `psql` session. Do not use the Supabase table editor for a multi-million-row import.

```powershell
$env:PGPASSWORD = $env:SUPABASE_DB_PASSWORD
psql $env:ENTERPRISE_DATABASE_URL -v ON_ERROR_STOP=1 `
  -f .\portfolio-rag\src\main\resources\db\migration\V1__enterprise_rag.sql
psql $env:ENTERPRISE_DATABASE_URL -v ON_ERROR_STOP=1 `
  -f .\portfolio-rag\src\main\resources\db\migration\V2__enterprise_rag_generations.sql
psql $env:ENTERPRISE_DATABASE_URL -v ON_ERROR_STOP=1 `
  -f .\portfolio-rag\src\main\resources\db\migration\V3__enterprise_contextual_retrieval.sql
```

Check `/api/enterprise/health` and `/api/enterprise/stats` before ingesting. `MIGRATION_REQUIRED` is a stop condition.

## Canary and resumable staging load

```powershell
$env:ENTERPRISE_DATABASE_URL = 'postgresql://...'
$env:DASHSCOPE_API_KEY = '...'
python .\eval\enterprise_rag_worker.py `
  --archive .\eval\data\EnterpriseRAG-Bench\github_slice_0001.zip `
  --max-documents 1000 --database-url $env:ENTERPRISE_DATABASE_URL `
  --chunk-tokens 700 --overlap-tokens 80 `
  --embedding-api-key $env:DASHSCOPE_API_KEY `
  --checkpoint .\eval\data\EnterpriseRAG-Bench\worker.sqlite3
```

The worker writes only to a new staging corpus and records every completed document in SQLite WAL mode. Re-run with `--resume` after a process/network failure. The v2 pipeline fingerprint rejects old or differently configured checkpoints. Add `--contextual-enabled` only for an explicitly budgeted canary; it adds one chat-model call per chunk. Inspect count, disk, WAL, tokens, cost and p95 latency before moving to 5K and 50K.

## Activation and rollback

```powershell
$headers = @{ 'X-Enterprise-Admin-Token' = $env:ENTERPRISE_RAG_ADMIN_TOKEN }
Invoke-RestMethod "$env:API_BASE/api/enterprise/admin/corpora/$CORPUS_ID/activate" -Method Post -Headers $headers
Invoke-RestMethod "$env:API_BASE/api/enterprise/admin/corpora/$PREVIOUS_CORPUS_ID/rollback" -Method Post -Headers $headers
```

Activation is permitted only after the application gate: manifest/counts, no orphan/duplicate rows, valid HNSW/GIN, smoke questions, evaluation baseline and backup availability. Ingestion pauses at disk soft limit or database read-only; the old ACTIVE pointer remains unchanged.

## Online verification

```powershell
Invoke-RestMethod "$env:API_BASE/api/enterprise/health"
Invoke-RestMethod "$env:API_BASE/api/enterprise/stats"
```

Verify Vercel `VITE_API_BASE_URL`, CORS preflight, SSE frames, bilingual state labels and source citations. Railway is an online API host; it is not the durable home for a multi-hour ingestion process.
