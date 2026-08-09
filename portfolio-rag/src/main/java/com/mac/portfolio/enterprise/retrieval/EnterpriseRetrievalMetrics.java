package com.mac.portfolio.enterprise.retrieval;

public record EnterpriseRetrievalMetrics(
        long vectorMs,
        long ftsMs,
        long rrfMs,
        long rerankMs,
        int candidateCount,
        int finalContextCount,
        String fallback) {

    public EnterpriseRetrievalMetrics(long vectorMs, long ftsMs, long rrfMs, long rerankMs,
                                      int candidateCount, int finalContextCount) {
        this(vectorMs, ftsMs, rrfMs, rerankMs, candidateCount, finalContextCount, null);
    }
}
