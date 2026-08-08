package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionTest {

    @Test
    void rewardsDocumentsPresentInBothRankedListsWithoutMixingRawScores() {
        EnterpriseSearchHit shared = hit("shared");
        EnterpriseSearchHit vectorOnly = hit("vector-only");
        EnterpriseSearchHit keywordOnly = hit("keyword-only");

        List<EnterpriseSearchHit> result = new RrfFusion().fuse(
                List.of(shared, vectorOnly), List.of(shared, keywordOnly), 60, 3);

        assertThat(result).extracting(EnterpriseSearchHit::chunkId)
                .contains("shared", "vector-only", "keyword-only");
        assertThat(result.getFirst().chunkId()).isEqualTo("shared");
        assertThat(result.getFirst().score()).isGreaterThan(result.get(1).score());
    }

    private EnterpriseSearchHit hit(String id) {
        return new EnterpriseSearchHit(id, "doc-" + id, id, "bench", "github", "Title", "Content",
                "default", "engineering", "public", 0, 0.99, 1, Map.of());
    }
}
