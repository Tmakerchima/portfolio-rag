package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseRetrievalStrategy;
import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;

import java.util.List;

public record EnterpriseRetrievalResult(
        List<EnterpriseSearchHit> hits,
        EnterpriseRetrievalStrategy strategy,
        EnterpriseRetrievalMetrics metrics) {
}
