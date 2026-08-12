-- V4 只为主库增加复制后校验、增量审计和人工排障使用的 B-tree 索引，不安装 pg_search。
-- Supabase managed PostgreSQL 不一定允许 CREATE EXTENSION pg_search；ParadeDB DDL 放在 deploy/paradedb/。
-- 保留 V1-V3 的 search_vector、GIN、embedding、HNSW，任何回滚都可以切回 POSTGRES_FTS。
-- 注意：这些普通索引不直接加速 PostgreSQL WAL logical replication，也不承担 BM25 检索。

CREATE INDEX IF NOT EXISTS enterprise_documents_bm25_sync_idx
    ON enterprise_documents (corpus_id, updated_at, document_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS enterprise_chunks_bm25_sync_idx
    ON enterprise_chunks (corpus_id, updated_at, chunk_id);

COMMENT ON INDEX enterprise_documents_bm25_sync_idx IS
    '用于复制后 validation、incremental audit 与人工 sync verification；不加速 WAL 复制，不承担 BM25 评分';
COMMENT ON INDEX enterprise_chunks_bm25_sync_idx IS
    '用于复制后 validation、incremental audit 与人工 sync verification；不加速 WAL 复制，不承担 BM25 评分';
