-- EnterpriseRAG generation-aware, additive migration.
-- Apply V1 first, then this file with psql or a reviewed migration runner.
-- It never deletes public.vector_store or any existing enterprise rows.

CREATE TABLE IF NOT EXISTS enterprise_corpora (
    corpus_id              UUID PRIMARY KEY,
    dataset_name           VARCHAR(256) NOT NULL,
    dataset_version        VARCHAR(128) NOT NULL,
    state                  VARCHAR(32) NOT NULL,
    embedding_provider     VARCHAR(128) NOT NULL DEFAULT '',
    embedding_model        VARCHAR(256) NOT NULL DEFAULT '',
    embedding_dimension    INTEGER NOT NULL DEFAULT 1024,
    chunker_version        VARCHAR(128) NOT NULL DEFAULT '',
    expected_documents     BIGINT NOT NULL DEFAULT 0,
    document_count         BIGINT NOT NULL DEFAULT 0,
    chunk_count            BIGINT NOT NULL DEFAULT 0,
    embedded_chunk_count   BIGINT NOT NULL DEFAULT 0,
    failed_count           BIGINT NOT NULL DEFAULT 0,
    metadata               JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at           TIMESTAMPTZ,
    retired_at             TIMESTAMPTZ,
    CONSTRAINT enterprise_corpora_state_check CHECK
        (state IN ('STAGING', 'EMBEDDING', 'INDEXING', 'VALIDATING', 'READY', 'ACTIVE', 'FAILED', 'RETIRED'))
);

INSERT INTO enterprise_corpora (corpus_id, dataset_name, dataset_version, state, chunker_version)
VALUES ('00000000-0000-0000-0000-000000000001', 'legacy-enterprise-v1', 'legacy', 'READY', 'legacy-v1')
ON CONFLICT (corpus_id) DO NOTHING;

ALTER TABLE enterprise_documents
    ADD COLUMN IF NOT EXISTS corpus_id UUID;
ALTER TABLE enterprise_documents
    ALTER COLUMN corpus_id SET DEFAULT '00000000-0000-0000-0000-000000000001';
UPDATE enterprise_documents
SET corpus_id = '00000000-0000-0000-0000-000000000001'
WHERE corpus_id IS NULL;
ALTER TABLE enterprise_documents
    ALTER COLUMN corpus_id SET NOT NULL;
ALTER TABLE enterprise_documents
    DROP CONSTRAINT IF EXISTS enterprise_documents_source_external_key;
CREATE UNIQUE INDEX IF NOT EXISTS enterprise_documents_corpus_source_external_key
    ON enterprise_documents (corpus_id, source, external_id);

ALTER TABLE enterprise_chunks
    ADD COLUMN IF NOT EXISTS corpus_id UUID;
ALTER TABLE enterprise_chunks
    ALTER COLUMN corpus_id SET DEFAULT '00000000-0000-0000-0000-000000000001';
UPDATE enterprise_chunks c
SET corpus_id = d.corpus_id
FROM enterprise_documents d
WHERE c.document_id = d.document_id
  AND c.corpus_id IS NULL;
UPDATE enterprise_chunks
SET corpus_id = '00000000-0000-0000-0000-000000000001'
WHERE corpus_id IS NULL;
ALTER TABLE enterprise_chunks
    ALTER COLUMN corpus_id SET NOT NULL;

UPDATE enterprise_corpora
SET state = 'ACTIVE', activated_at = now()
WHERE corpus_id = '00000000-0000-0000-0000-000000000001'
  AND EXISTS (SELECT 1 FROM enterprise_documents WHERE corpus_id = enterprise_corpora.corpus_id)
  AND NOT EXISTS (SELECT 1 FROM enterprise_corpora WHERE state = 'ACTIVE');
CREATE INDEX IF NOT EXISTS enterprise_chunks_corpus_idx
    ON enterprise_chunks (corpus_id, document_id);
CREATE INDEX IF NOT EXISTS enterprise_documents_corpus_acl_idx
    ON enterprise_documents (corpus_id, tenant_id, department, access_level)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS enterprise_corpora_single_active_idx
    ON enterprise_corpora ((state)) WHERE state = 'ACTIVE';

CREATE TABLE IF NOT EXISTS enterprise_ingestion_jobs (
    job_id                 UUID PRIMARY KEY,
    corpus_id              UUID NOT NULL REFERENCES enterprise_corpora(corpus_id),
    status                 VARCHAR(32) NOT NULL,
    archive_cursor         TEXT NOT NULL DEFAULT '',
    documents_processed    BIGINT NOT NULL DEFAULT 0,
    chunks_processed       BIGINT NOT NULL DEFAULT 0,
    tokens_processed       BIGINT NOT NULL DEFAULT 0,
    failed_count           BIGINT NOT NULL DEFAULT 0,
    attempts               INTEGER NOT NULL DEFAULT 0,
    last_error_code        VARCHAR(128) NOT NULL DEFAULT '',
    metadata               JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at            TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS enterprise_ingestion_jobs_corpus_idx
    ON enterprise_ingestion_jobs (corpus_id, updated_at DESC);
