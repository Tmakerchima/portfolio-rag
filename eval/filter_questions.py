#!/usr/bin/env python3
"""按当前 Enterprise corpus 的 external_id 过滤可公平评测的问题集。"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--questions", type=Path, required=True)
    parser.add_argument("--document-ids", type=Path, required=True,
                        help="一行一个 external_id，或包含 external_id 字段的 JSON/JSONL 文件")
    parser.add_argument("--output-dir", type=Path, required=True)
    return parser.parse_args()


def load_document_ids(path: Path) -> set[str]:
    """读取 SQL 导出的 ID 文件或简单 JSON/JSONL，避免把数据库连接写进 evaluator。"""
    text = path.read_text(encoding="utf-8")
    ids: set[str] = set()
    for line in text.splitlines():
        value = line.strip()
        if not value:
            continue
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError:
            ids.add(value)
            continue
        if isinstance(parsed, str):
            ids.add(parsed)
        elif isinstance(parsed, dict) and parsed.get("external_id"):
            ids.add(str(parsed["external_id"]))
        elif isinstance(parsed, list):
            ids.update(str(item) for item in parsed)
    return ids


def classify(question: dict, document_ids: set[str]) -> str:
    expected = {str(value) for value in question.get("expected_doc_ids", [])}
    if not expected:
        return "unsupported"
    matched = expected & document_ids
    if matched == expected:
        return "fully_supported"
    if matched:
        return "partially_supported"
    return "unsupported"


def main() -> int:
    args = parse_args()
    document_ids = load_document_ids(args.document_ids)
    groups = {"fully_supported": [], "partially_supported": [], "unsupported": []}
    with args.questions.open(encoding="utf-8") as stream:
        for line in stream:
            if not line.strip():
                continue
            question = json.loads(line)
            groups[classify(question, document_ids)].append(question)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    for name, values in groups.items():
        (args.output_dir / f"{name}.jsonl").write_text(
            "".join(json.dumps(value, ensure_ascii=False) + "\n" for value in values),
            encoding="utf-8")
    manifest = {
        "status": "measured",
        "document_count": len(document_ids),
        "question_count": sum(len(values) for values in groups.values()),
        "fully_supported": len(groups["fully_supported"]),
        "partially_supported": len(groups["partially_supported"]),
        "unsupported": len(groups["unsupported"]),
        "outputs": {name: str(args.output_dir / f"{name}.jsonl") for name in groups},
    }
    (args.output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
