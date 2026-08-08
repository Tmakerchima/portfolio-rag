# EnterpriseRAG disaster recovery

## Recovery order

1. Application level: use the protected rollback endpoint to point queries at the previous READY/RETIRED corpus. This does not delete staging rows and should be the normal response to retrieval regression.
2. Data level: pause the ingestion worker, leave failed generation in `FAILED`, and preserve its checkpoint/dead-letter manifest for diagnosis.
3. Database level: use a verified logical backup or Supabase PITR only after an approved maintenance window. Database restore can cause downtime and needs explicit user confirmation.

## Before production migration

- Confirm Supabase backup/PITR status and record the latest recoverable timestamp.
- Export the affected schema/control tables with a timestamp and SHA-256 outside the repository.
- Restore the export into a local/staging PostgreSQL instance and run schema, count and query smoke tests.
- Record backup path/hash, restore command, operator, start/end time and observed downtime in the deployment ticket.

## Failure matrix

| Failure | Action |
|---|---|
| Embedding 429/5xx/timeout | Exponential backoff, bounded retry, then dead-letter and checkpoint |
| Worker killed | Re-run with `--resume`; completed content hashes are skipped |
| Transaction/write failure | Roll back bounded document batch; ACTIVE corpus unchanged |
| Disk at soft limit/read-only | Pause immediately; do not retry-write in a loop |
| Index build failure | Keep generation non-active; serve lexical validation only if explicitly enabled |
| Vector query failure | Return FTS-only response and expose degraded status |
| FTS failure | Return vector-only response and expose degraded status |
| Reranker failure | Keep RRF order |
| LLM failure | Return evidence and retryable structured error |

The system must never claim that a canary is a complete 500K corpus.
