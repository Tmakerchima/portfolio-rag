-- Separate citable source text from retrieval-only contextual augmentation.
-- Apply after V1 and V2. Existing rows keep their current content as index_content.

ALTER TABLE enterprise_chunks
    ADD COLUMN IF NOT EXISTS contextual_prefix TEXT NOT NULL DEFAULT '';
ALTER TABLE enterprise_chunks
    ADD COLUMN IF NOT EXISTS index_content TEXT NOT NULL DEFAULT '';
ALTER TABLE enterprise_documents
    ADD COLUMN IF NOT EXISTS index_fingerprint VARCHAR(128) NOT NULL DEFAULT 'legacy-v1';

UPDATE enterprise_chunks
SET index_content = content
WHERE index_content = '';

CREATE OR REPLACE FUNCTION enterprise_chunks_search_vector_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.index_content := CASE
        WHEN coalesce(NEW.index_content, '') = '' THEN coalesce(NEW.content, '')
        ELSE NEW.index_content
    END;
    NEW.search_vector := to_tsvector('simple', NEW.index_content);
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS enterprise_chunks_search_vector_trigger ON enterprise_chunks;
CREATE TRIGGER enterprise_chunks_search_vector_trigger
    BEFORE INSERT OR UPDATE OF content, contextual_prefix, index_content ON enterprise_chunks
    FOR EACH ROW EXECUTE FUNCTION enterprise_chunks_search_vector_update();

UPDATE enterprise_chunks
SET search_vector = to_tsvector('simple', index_content), updated_at = now();

COMMENT ON COLUMN enterprise_chunks.content IS
    'Original citable source text. Never contains generated contextual text for v2+ chunks.';
COMMENT ON COLUMN enterprise_chunks.contextual_prefix IS
    'Generated retrieval-only context. Not source evidence.';
COMMENT ON COLUMN enterprise_chunks.index_content IS
    'Text used for embedding and lexical indexing: contextual_prefix plus original content.';
COMMENT ON COLUMN enterprise_documents.index_fingerprint IS
    'Hash of chunker, contextualization and embedding configuration; changes force reindex even when source text is unchanged.';
