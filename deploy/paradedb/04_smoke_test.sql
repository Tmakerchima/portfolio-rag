-- 该查询必须返回 BM25 score，而不是 PostgreSQL ts_rank/ts_rank_cd。
SELECT c.chunk_id,
       c.content,
       pdb.score(c.chunk_id) AS bm25_score
FROM enterprise_chunks c
WHERE c.index_content ||| 'TS-999 upload worker'
ORDER BY pdb.score(c.chunk_id) DESC, c.chunk_id ASC
LIMIT 5;

-- Contextual BM25 检查：query 中的 ACME/Q2/2025 可以只出现在 contextual_prefix。
SELECT chunk_id, index_content, pdb.score(chunk_id) AS bm25_score
FROM enterprise_chunks
WHERE index_content ||| 'ACME Q2 2025 revenue growth'
ORDER BY pdb.score(chunk_id) DESC, chunk_id ASC
LIMIT 5;
