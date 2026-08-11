-- V4 只为主库增加可选的同步辅助索引和说明，不安装 pg_search。
-- Supabase managed PostgreSQL 不一定允许 CREATE EXTENSION pg_search；ParadeDB DDL 放在 deploy/paradedb/。
-- 保留 V1-V3 的 search_vector、GIN、embedding、HNSW，任何回滚都可以切回 POSTGRES_FTS。

CREATE INDEX IF NOT EXISTS enterprise_documents_bm25_sync_idx
    ON enterprise_documents (corpus_id, updated_at, document_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS enterprise_chunks_bm25_sync_idx
    ON enterprise_chunks (corpus_id, updated_at, chunk_id);

COMMENT ON INDEX enterprise_documents_bm25_sync_idx IS
    '用于 Supabase -> ParadeDB 逻辑复制/校验的增量扫描辅助索引，不承担 BM25 评分';
COMMENT ON INDEX enterprise_chunks_bm25_sync_idx IS
    '用于 Supabase -> ParadeDB 逻辑复制/校验的增量扫描辅助索引，不承担 BM25 评分';
