package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseAccessContext;

/**
 * 企业 lexical 检索的统一入口。
 *
 * <p>上层只关心“按权限检索关键词”，不再把 PostgreSQL FTS 或 ParadeDB
 * 的具体 SQL 写死在混合检索流程里。这样才能在不改 API 的情况下切换
 * 真正的 BM25，或者在 BM25 不可用时安全降级。</p>
 */
public interface EnterpriseLexicalRetriever {

    EnterpriseLexicalSearchResult search(String query, EnterpriseAccessContext access, int topK);

    /** 当前配置的后端名称，例如 POSTGRES_FTS 或 PARADEDB_BM25。 */
    String configuredBackend();

    /** 与部署文档中的 backendName 术语保持兼容；路由器返回 configured backend。 */
    default String backendName() {
        return configuredBackend();
    }

    /** 健康检查只返回能力状态，不返回连接串、用户名或密码。 */
    EnterpriseLexicalHealth health();

    default boolean healthy() {
        return health().healthy();
    }
}
