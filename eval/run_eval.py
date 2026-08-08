#!/usr/bin/env python3
"""Call the EnterpriseRAG SSE API and save answer/source predictions as JSONL."""

from __future__ import annotations

import argparse
import json
import urllib.request
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--questions", type=Path, required=True)
    parser.add_argument("--api-base", default="http://localhost:8080")
    parser.add_argument("--strategy", default="HYBRID", choices=["VECTOR", "KEYWORD", "HYBRID", "HYBRID_RERANK"])
    parser.add_argument("--role", default="admin", choices=["public", "engineering", "finance", "hr", "admin"])
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--limit", type=int)
    return parser.parse_args()


def stream_answer(api_base: str, question: str, role: str, strategy: str) -> tuple[str, list[str], list[str]]:
    body = json.dumps({"question": question, "role": role, "strategy": strategy}).encode("utf-8")
    request = urllib.request.Request(api_base.rstrip("/") + "/api/enterprise/chat", data=body, method="POST", headers={
        "Content-Type": "application/json", "Accept": "text/event-stream",
    })
    answer: list[str] = []
    document_ids: list[str] = []
    contexts: list[str] = []
    with urllib.request.urlopen(request) as response:
        for raw_line in response:
            line = raw_line.decode("utf-8").strip()
            if not line.startswith("data:"):
                continue
            data = line[5:].lstrip()
            if data.startswith("@@SOURCES@@"):
                frame = json.loads(data[len("@@SOURCES@@"):])
                document_ids = [source["document_id"] for source in frame.get("sources", [])]
                contexts = [source.get("chunk", "") for source in frame.get("sources", [])]
            elif data.startswith("@@METRICS@@") or data.startswith("@@ERROR@@"):
                continue
            else:
                answer.append(data)
    return "".join(answer), document_ids, contexts


def main() -> int:
    args = parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with args.questions.open(encoding="utf-8") as questions, args.output.open("w", encoding="utf-8") as output:
        for line in questions:
            if args.limit is not None and count >= args.limit:
                break
            item = json.loads(line)
            answer, document_ids, contexts = stream_answer(args.api_base, item["question"], args.role, args.strategy)
            output.write(json.dumps({
                "question_id": item["question_id"],
                "question": item["question"],
                "gold_answer": item.get("gold_answer", ""),
                "answer": answer,
                "document_ids": document_ids,
                "contexts": contexts,
            }, ensure_ascii=False) + "\n")
            count += 1
            print(f"evaluated {count}: {item['question_id']}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
