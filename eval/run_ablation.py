#!/usr/bin/env python3
"""Run the same gold questions through each EnterpriseRAG strategy without fabricating metrics."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path


STRATEGIES = ("VECTOR", "KEYWORD", "HYBRID", "HYBRID_RERANK")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--questions", type=Path, required=True)
    parser.add_argument("--api-base", default="http://localhost:8080")
    parser.add_argument("--role", default="admin", choices=["public", "engineering", "finance", "hr", "admin"])
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--limit", type=int)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    manifest = {"status": "measured", "strategies": {}, "lexical_backends": {}}
    for strategy in STRATEGIES:
        predictions = args.output_dir / f"{strategy.lower()}-predictions.jsonl"
        metrics = args.output_dir / f"{strategy.lower()}-metrics.json"
        command = [sys.executable, str(Path(__file__).with_name("run_eval.py")),
                   "--questions", str(args.questions), "--api-base", args.api_base,
                   "--strategy", strategy, "--role", args.role, "--output", str(predictions)]
        if args.limit is not None:
            command.extend(["--limit", str(args.limit)])
        try:
            subprocess.run(command, check=True)
            evaluate = [sys.executable, str(Path(__file__).with_name("retrieval_eval.py")),
                        "--questions", str(args.questions), "--predictions", str(predictions),
                        "--output", str(metrics)]
            subprocess.run(evaluate, check=True)
            manifest["strategies"][strategy] = {"predictions": str(predictions), "metrics": str(metrics)}
            # 从 API 的真实 metrics frame 汇总 backend；没有 frame 就保持 unknown，不补造数值。
            backends = set()
            for line in predictions.read_text(encoding="utf-8").splitlines():
                backend = json.loads(line).get("metrics", {}).get("lexical_backend")
                if backend:
                    backends.add(backend)
            manifest["lexical_backends"][strategy] = sorted(backends) or ["unknown"]
        except (OSError, subprocess.CalledProcessError) as error:
            manifest["status"] = "SKIPPED_EXTERNAL_DEPENDENCY"
            manifest["strategies"][strategy] = {"status": "SKIPPED_EXTERNAL_DEPENDENCY", "reason": str(error)}
            break
    (args.output_dir / "ablation-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
