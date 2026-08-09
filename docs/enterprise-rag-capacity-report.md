# EnterpriseRAG capacity report

Status: **BLOCKED_BY_SUPABASE_CAPACITY until a paid capacity/budget gate is approved.**

## Current evidence

- The current Supabase project is on the Free tier from the supplied dashboard screenshot.
- The online API previously returned `MIGRATION_REQUIRED`, `documents=0`, `chunks=0`.
- The visible `public.vector_store` contains a legacy Resume projection, not EnterpriseRAG-Bench; the current Resume request path no longer queries it.
- The complete archive must be downloaded and verified before any production load. The local `.partial`/range files are not valid input.

## Estimate

The local 5,000-document GitHub slice, using the former 1,600-character chunk configuration, measured roughly 3.477 chunks/document. A rough projection is therefore approximately 1.74M chunks for 500K documents. A 1024-dimensional float32 payload alone is approximately 6.64 GiB before content, JSONB, row overhead, HNSW, GIN, WAL, index-build temporary space and backups.

This is a planning estimate, not a claim of the final dataset size. The worker must produce the real manifest and canary measurements before any full run.

## Required measurements

For 1K, 5K and 50K canaries record:

- document/chunk/content bytes;
- vector and relation sizes;
- HNSW/GIN sizes and build time;
- WAL growth and backup headroom;
- embedding tokens, cost and throughput;
- query p50/p95/p99 and retrieval metrics.

The full run is blocked unless projected final data plus index/WAL/backup headroom remains below the provisioned disk safety threshold and `MAX_EMBEDDING_COST_CNY` is explicitly set. Supabase plan upgrades and billing changes require user confirmation.
