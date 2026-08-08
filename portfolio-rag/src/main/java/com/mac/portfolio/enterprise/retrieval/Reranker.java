package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;

import java.util.List;

public interface Reranker {
    List<EnterpriseSearchHit> rerank(String query, List<EnterpriseSearchHit> candidates);
}
