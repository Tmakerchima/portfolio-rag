#!/usr/bin/env python3
"""Optional Ragas adapter; reports NOT_EXECUTED when the installed API/provider is unavailable."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result: dict[str, object]
    try:
        from datasets import Dataset
        from ragas import evaluate
        from ragas.metrics import ContextPrecision, ContextRecall, Faithfulness, ResponseRelevancy

        rows = []
        for line in args.predictions.open(encoding="utf-8"):
            item = json.loads(line)
            rows.append({
                "question": item.get("question", item.get("question_id", "")),
                "ground_truth": item.get("gold_answer", ""),
                "answer": item.get("answer", ""),
                "contexts": item.get("contexts", []),
            })
        if not rows:
            raise ValueError("prediction file is empty")
        evaluation = evaluate(Dataset.from_list(rows), metrics=[
            Faithfulness(), ContextPrecision(), ContextRecall(), ResponseRelevancy(),
        ])
        result = {"status": "executed", "scores": dict(evaluation)}
    except Exception as error:  # optional provider/version issues are a valid non-execution state
        result = {"status": "NOT_EXECUTED", "reason": type(error).__name__}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
