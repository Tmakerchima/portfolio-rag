package com.mac.portfolio.enterprise.retrieval;

public record EnterpriseRetrievalMetrics(
        long vectorMs,
        long ftsMs,
        long rrfMs,
        long rerankMs,
        int candidateCount,
        int finalContextCount,
        String fallback,
        int queryCount) {

    public EnterpriseRetrievalMetrics(long vectorMs, long ftsMs, long rrfMs, long rerankMs,
                                      int candidateCount, int finalContextCount, String fallback) {
        this(vectorMs, ftsMs, rrfMs, rerankMs, candidateCount, finalContextCount, fallback, 1);
    }

    public EnterpriseRetrievalMetrics(long vectorMs, long ftsMs, long rrfMs, long rerankMs,
                                      int candidateCount, int finalContextCount) {
        this(vectorMs, ftsMs, rrfMs, rerankMs, candidateCount, finalContextCount, null, 1);
    }
}
