-- 将“可引用来源原文”与“只用于检索的上下文增强文本”分开保存。
-- 必须在 V1、V2 后执行；旧数据先用原 content 回填 index_content，保证迁移后仍可检索。

-- contextual_prefix 保存模型生成的定位前缀；index_content 保存真正用于索引的完整文本。
ALTER TABLE enterprise_chunks
    ADD COLUMN IF NOT EXISTS contextual_prefix TEXT NOT NULL DEFAULT '';
ALTER TABLE enterprise_chunks
    ADD COLUMN IF NOT EXISTS index_content TEXT NOT NULL DEFAULT '';
ALTER TABLE enterprise_documents
    ADD COLUMN IF NOT EXISTS index_fingerprint VARCHAR(128) NOT NULL DEFAULT 'legacy-v1';

-- 旧 Chunk 没有上下文前缀，因此索引文本就等于原始可引用正文。
UPDATE enterprise_chunks
SET index_content = content
WHERE index_content = '';

CREATE OR REPLACE FUNCTION enterprise_chunks_search_vector_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- 调用方没有显式传 index_content 时自动退回原文，避免生成空全文索引。
    NEW.index_content := CASE
        WHEN coalesce(NEW.index_content, '') = '' THEN coalesce(NEW.content, '')
        ELSE NEW.index_content
    END;
    -- 每次写入或更新 Chunk 时同步重建 PostgreSQL FTS 向量。
    NEW.search_vector := to_tsvector('simple', NEW.index_content);
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS enterprise_chunks_search_vector_trigger ON enterprise_chunks;
-- 监听原文、上下文前缀和索引文本变化，确保 search_vector 不会过期。
CREATE TRIGGER enterprise_chunks_search_vector_trigger
    BEFORE INSERT OR UPDATE OF content, contextual_prefix, index_content ON enterprise_chunks
    FOR EACH ROW EXECUTE FUNCTION enterprise_chunks_search_vector_update();

-- 为迁移前已经存在的所有 Chunk 立即重建一次全文索引。
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
