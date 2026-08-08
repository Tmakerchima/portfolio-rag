#!/usr/bin/env python3
"""Compute retrieval metrics only where the benchmark provides gold document IDs."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

KS = (1, 3, 5, 10)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--questions", type=Path, required=True)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def ndcg(retrieved: list[str], expected: set[str], k: int) -> float:
    if not expected:
        return math.nan
    dcg = sum((1.0 / math.log2(index + 2)) for index, doc_id in enumerate(retrieved[:k]) if doc_id in expected)
    ideal = sum((1.0 / math.log2(index + 2)) for index in range(min(k, len(expected))))
    return dcg / ideal if ideal else 0.0


def main() -> int:
    args = parse_args()
    questions = {json.loads(line)["question_id"]: json.loads(line) for line in args.questions.open(encoding="utf-8")}
    predictions = {json.loads(line)["question_id"]: json.loads(line) for line in args.predictions.open(encoding="utf-8")}
    supported = [(questions[key], predictions[key]) for key in questions.keys() & predictions.keys()
                 if questions[key].get("expected_doc_ids")]
    result: dict[str, object] = {
        "status": "measured" if supported else "unsupported",
        "supported_questions": len(supported),
        "ks": {},
    }
    for k in KS:
        hit_rates: list[float] = []
        recalls: list[float] = []
        precisions: list[float] = []
        reciprocal_ranks: list[float] = []
        ndcgs: list[float] = []
        for question, prediction in supported:
            expected = set(question["expected_doc_ids"])
            retrieved = prediction.get("document_ids", [])
            top = retrieved[:k]
            overlap = expected.intersection(top)
            hit_rates.append(1.0 if overlap else 0.0)
            recalls.append(len(overlap) / len(expected))
            precisions.append(len(overlap) / max(1, len(top)))
            reciprocal_ranks.append(next((1.0 / (index + 1) for index, doc_id in enumerate(retrieved) if doc_id in expected), 0.0))
            ndcgs.append(ndcg(retrieved, expected, k))
        result["ks"][str(k)] = {
            "hit_rate": sum(hit_rates) / len(hit_rates) if hit_rates else None,
            "recall": sum(recalls) / len(recalls) if recalls else None,
            "precision": sum(precisions) / len(precisions) if precisions else None,
            "mrr": sum(reciprocal_ranks) / len(reciprocal_ranks) if reciprocal_ranks else None,
            "ndcg": sum(ndcgs) / len(ndcgs) if ndcgs else None,
        }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
