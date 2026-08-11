package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;
import java.util.List;

/** Test/fallback implementation. Production uses ConfigurableEnterpriseReranker. */
public class NoOpReranker implements Reranker {
    @Override
    public List<EnterpriseSearchHit> rerank(String query, List<EnterpriseSearchHit> candidates) {
        return List.copyOf(candidates);
    }
}
