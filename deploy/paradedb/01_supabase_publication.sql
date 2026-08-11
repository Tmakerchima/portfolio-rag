-- 在 Supabase primary 上执行；不要在 Spring 启动时自动执行。
-- 如果项目通过 Dashboard 管理 replication，请把本文件当作人工审核清单。
CREATE PUBLICATION enterprise_rag_publication
    FOR TABLE enterprise_documents, enterprise_chunks, enterprise_corpora;

-- 生产环境还需要平台侧创建 replication slot / 复制用户、配置网络白名单，
-- 以及确认 publication 对包含 index_content 的列可见；这些权限不能由仓库安全地代办。
