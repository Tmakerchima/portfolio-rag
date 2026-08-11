package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;

import java.util.List;

/** 一次 lexical 查询的结果，同时携带实际后端和降级原因。 */
public record EnterpriseLexicalSearchResult(
        List<EnterpriseSearchHit> hits,
        String backend,
        String fallbackReason) {

    public EnterpriseLexicalSearchResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
        backend = backend == null || backend.isBlank() ? "UNKNOWN" : backend;
    }

    public static EnterpriseLexicalSearchResult of(List<EnterpriseSearchHit> hits, String backend) {
        return new EnterpriseLexicalSearchResult(hits, backend, null);
    }

    public static EnterpriseLexicalSearchResult fallback(List<EnterpriseSearchHit> hits,
                                                          String backend, String reason) {
        return new EnterpriseLexicalSearchResult(hits, backend, reason);
    }
}
