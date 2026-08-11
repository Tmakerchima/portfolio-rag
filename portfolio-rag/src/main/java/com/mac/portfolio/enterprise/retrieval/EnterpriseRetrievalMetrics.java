package com.mac.portfolio.enterprise.retrieval;

public record EnterpriseRetrievalMetrics(
        long vectorMs,
        long ftsMs,
        long rrfMs,
        long rerankMs,
        int candidateCount,
        int finalContextCount,
        String fallback,
        int queryCount,
        String lexicalBackend) {

    public EnterpriseRetrievalMetrics(long vectorMs, long ftsMs, long rrfMs, long rerankMs,
                                      int candidateCount, int finalContextCount, String fallback) {
        this(vectorMs, ftsMs, rrfMs, rerankMs, candidateCount, finalContextCount, fallback, 1, "POSTGRES_FTS");
    }

    public EnterpriseRetrievalMetrics(long vectorMs, long ftsMs, long rrfMs, long rerankMs,
                                      int candidateCount, int finalContextCount) {
        this(vectorMs, ftsMs, rrfMs, rerankMs, candidateCount, finalContextCount, null, 1, "POSTGRES_FTS");
    }

    public EnterpriseRetrievalMetrics(long vectorMs, long ftsMs, long rrfMs, long rerankMs,
                                      int candidateCount, int finalContextCount, String fallback,
                                      int queryCount) {
        this(vectorMs, ftsMs, rrfMs, rerankMs, candidateCount, finalContextCount, fallback, queryCount,
                "POSTGRES_FTS");
    }
}
