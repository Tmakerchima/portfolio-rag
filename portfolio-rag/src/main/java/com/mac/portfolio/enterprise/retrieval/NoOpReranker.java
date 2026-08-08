package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoOpReranker implements Reranker {
    @Override
    public List<EnterpriseSearchHit> rerank(String query, List<EnterpriseSearchHit> candidates) {
        return List.copyOf(candidates);
    }
}
