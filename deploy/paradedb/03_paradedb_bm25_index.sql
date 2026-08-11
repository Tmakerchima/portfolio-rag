-- ParadeDB search replica：只在 schema/初始复制准备好后执行。
-- index_content 是 Contextual Embedding 与 Contextual BM25 的共同检索表示。
-- chunk_id 是 varchar 主键，因此使用 literal tokenizer，满足 key_field 不分词要求。
-- ICU 对中文、英文、错误码和技术文档的 Unicode 边界更稳妥；不要把 prefix 当 citation。
CREATE INDEX IF NOT EXISTS enterprise_chunks_index_content_paradedb_idx
ON enterprise_chunks
USING paradedb (
    (chunk_id::pdb.literal),
    (index_content::pdb.icu),
    corpus_id,
    document_id,
    chunk_index
)
WITH (key_field = 'chunk_id');

-- ParadeDB 每张表只能有一个 ParadeDB index；若字段/tokenizer 改动，请按版本文档重建该 index。
