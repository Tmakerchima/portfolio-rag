package com.mac.portfolio.enterprise.retrieval;

import com.mac.portfolio.enterprise.model.EnterpriseSearchHit;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reciprocal Rank Fusion keeps vector and FTS score scales independent. */
@Component
public class RrfFusion {

    public List<EnterpriseSearchHit> fuse(List<EnterpriseSearchHit> vectorHits,
                                          List<EnterpriseSearchHit> keywordHits,
                                          int rrfK,
                                          int finalTopK) {
        if (rrfK < 1 || finalTopK < 1) return List.of();
        Map<String, EnterpriseSearchHit> hits = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();
        add(hits, scores, vectorHits, rrfK);
        add(hits, scores, keywordHits, rrfK);
        List<EnterpriseSearchHit> ranked = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(finalTopK)
                .map(entry -> hits.get(entry.getKey()).withScore(entry.getValue(), 0))
                .toList();
        return java.util.stream.IntStream.range(0, ranked.size())
                .mapToObj(index -> ranked.get(index).withScore(ranked.get(index).score(), index + 1))
                .toList();
    }

    public List<EnterpriseSearchHit> fuseAll(List<List<EnterpriseSearchHit>> rankedLists,
                                             int rrfK,
                                             int finalTopK) {
        if (rankedLists == null || rrfK < 1 || finalTopK < 1) return List.of();
        Map<String, EnterpriseSearchHit> hits = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();
        for (List<EnterpriseSearchHit> ranked : rankedLists) add(hits, scores, ranked, rrfK);
        List<EnterpriseSearchHit> values = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(finalTopK)
                .map(entry -> hits.get(entry.getKey()).withScore(entry.getValue(), 0))
                .toList();
        return java.util.stream.IntStream.range(0, values.size())
                .mapToObj(index -> values.get(index).withScore(values.get(index).score(), index + 1))
                .toList();
    }

    private void add(Map<String, EnterpriseSearchHit> hits, Map<String, Double> scores,
                     List<EnterpriseSearchHit> rankedHits, int rrfK) {
        if (rankedHits == null) return;
        for (int index = 0; index < rankedHits.size(); index++) {
            EnterpriseSearchHit hit = rankedHits.get(index);
            double contribution = 1.0 / (rrfK + index + 1);
            hits.putIfAbsent(hit.chunkId(), hit);
            scores.merge(hit.chunkId(), contribution, Double::sum);
        }
    }
}
