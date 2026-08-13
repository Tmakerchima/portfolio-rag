-- Read-only export for eval/filter_questions.py. Run against the intended database.
SELECT DISTINCT d.external_id
FROM enterprise_documents d
JOIN enterprise_corpora c ON c.corpus_id = d.corpus_id
WHERE c.state = 'ACTIVE'
  AND d.deleted_at IS NULL
ORDER BY d.external_id;
