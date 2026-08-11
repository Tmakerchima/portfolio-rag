-- 在 ParadeDB search replica 上执行。
-- 把尖括号占位符替换为 secret manager 的临时渲染结果，绝不要提交真实密码。
CREATE SUBSCRIPTION enterprise_rag_from_supabase
    CONNECTION 'host=<SUPABASE_HOST> port=5432 dbname=<SUPABASE_DB> user=<REPLICATION_USER> password=<REPLICATION_PASSWORD> sslmode=require'
    PUBLICATION enterprise_rag_publication;

-- 检查复制：
-- SELECT subname, received_lsn, latest_end_lsn, latest_end_time FROM pg_stat_subscription;
