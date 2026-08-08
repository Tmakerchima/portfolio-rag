package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reciprocal Rank Fusion keeps vector and FTS score scales independent. */
public class RrfFusion {

    public List<EnterpriseSearchHit> fuse(List<EnterpriseSearchHit> vectorHits,
                                          List<EnterpriseSearchHit> keywordHits,
                                          int rrfK,
                                          int finalTopK) {
        Map<String, FusionScore> scores = new LinkedHashMap<>();
        add(scores, vectorHits, rrfK);
        add(scores, keywordHits, rrfK);
        return scores.values().stream()
                .sorted(Comparator.comparingDouble(FusionScore::score).reversed()
                        .thenComparing(value -> value.hit().chunkId()))
                .limit(finalTopK)
                .map(value -> value.hit().withScore(value.score(), value.rank()))
                .toList();
    }

    private void add(Map<String, FusionScore> scores, List<EnterpriseSearchHit> hits, int rrfK) {
        if (hits == null) return;
        for (int index = 0; index < hits.size(); index++) {
            EnterpriseSearchHit hit = hits.get(index);
            FusionScore current = scores.get(hit.chunkId());
            double contribution = 1.0 / (rrfK + index + 1);
            if (current == null) scores.put(hit.chunkId(), new FusionScore(hit, contribution, 0));
            else scores.put(hit.chunkId(), new FusionScore(current.hit(), current.score() + contribution, current.rank()));
        }
        int rank = 1;
        for (Map.Entry<String, FusionScore> entry : scores.entrySet()) {
            FusionScore value = entry.getValue();
            if (value.rank() == 0) entry.setValue(new FusionScore(value.hit(), value.score(), rank++));
        }
    }

    private record FusionScore(EnterpriseSearchHit hit, double score, int rank) {}
}
