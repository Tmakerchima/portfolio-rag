-- EnterpriseRAG additive migration.
-- Apply manually to the intended Supabase database after reviewing it.
-- It never touches the existing Spring AI vector_store table.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS enterprise_documents (
    document_id    VARCHAR(128) PRIMARY KEY,
    external_id    VARCHAR(512) NOT NULL,
    source         VARCHAR(128) NOT NULL,
    source_type    VARCHAR(64) NOT NULL,
    title          TEXT NOT NULL DEFAULT '',
    content        TEXT NOT NULL,
    content_hash   CHAR(64) NOT NULL,
    version        INTEGER NOT NULL DEFAULT 1,
    tenant_id      VARCHAR(128) NOT NULL DEFAULT 'default',
    department     VARCHAR(64) NOT NULL DEFAULT 'engineering',
    access_level   VARCHAR(32) NOT NULL DEFAULT 'public',
    metadata       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    indexed_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT enterprise_documents_source_external_key UNIQUE (source, external_id)
);

CREATE TABLE IF NOT EXISTS enterprise_chunks (
    chunk_id       VARCHAR(256) PRIMARY KEY,
    document_id    VARCHAR(128) NOT NULL REFERENCES enterprise_documents(document_id) ON DELETE CASCADE,
    chunk_index    INTEGER NOT NULL,
    content        TEXT NOT NULL,
    content_hash   CHAR(64) NOT NULL,
    token_count    INTEGER NOT NULL DEFAULT 0,
    metadata       JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding      vector(1024),
    search_vector  TSVECTOR NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT enterprise_chunks_document_index_key UNIQUE (document_id, chunk_index)
);

CREATE OR REPLACE FUNCTION enterprise_chunks_search_vector_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', coalesce(NEW.content, ''));
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS enterprise_chunks_search_vector_trigger ON enterprise_chunks;
CREATE TRIGGER enterprise_chunks_search_vector_trigger
    BEFORE INSERT OR UPDATE OF content ON enterprise_chunks
    FOR EACH ROW EXECUTE FUNCTION enterprise_chunks_search_vector_update();

CREATE INDEX IF NOT EXISTS enterprise_documents_acl_idx
    ON enterprise_documents (tenant_id, department, access_level)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS enterprise_documents_source_type_idx
    ON enterprise_documents (source_type)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS enterprise_chunks_document_idx
    ON enterprise_chunks (document_id);
CREATE INDEX IF NOT EXISTS enterprise_chunks_search_vector_gin_idx
    ON enterprise_chunks USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS enterprise_chunks_embedding_hnsw_idx
    ON enterprise_chunks USING hnsw (embedding vector_cosine_ops);
