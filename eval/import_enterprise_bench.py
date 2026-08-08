#!/usr/bin/env python3
"""Import the official EnterpriseRAG-Bench .txt export through the protected API."""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.request
import zipfile
from collections import Counter
from pathlib import Path
from typing import Iterable

ALLOWED_SIZES = {1000, 5000, 10000, 50000}
DSID_RE = re.compile(r"^(dsid_[^_]+)")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, required=True, help="all_documents.zip, source slice zip, or extracted directory")
    parser.add_argument("--max-documents", type=int, choices=sorted(ALLOWED_SIZES), default=5000)
    parser.add_argument("--api-base", default="http://localhost:8080")
    parser.add_argument("--admin-token", required=True)
    parser.add_argument("--tenant-id", default="default")
    parser.add_argument("--department", default="engineering")
    parser.add_argument("--access-level", default="public")
    parser.add_argument("--batch-size", type=int, default=25)
    return parser.parse_args()


def source_type(path_name: str) -> str:
    parts = [part for part in Path(path_name).parts if part not in (".", "")]
    if len(parts) > 1:
        return parts[0].lower().replace(" ", "_")
    return "unknown"


def external_id(path_name: str) -> str:
    match = DSID_RE.match(Path(path_name).stem)
    return match.group(1) if match else Path(path_name).stem


def iter_entries(path: Path, include_content: bool) -> Iterable[tuple[str, str, str | None]]:
    if path.is_dir():
        for file in sorted(path.rglob("*.txt")):
            kind = source_type(str(file.relative_to(path)))
            yield file.name, kind, file.read_text(encoding="utf-8") if include_content else None
        return
    with zipfile.ZipFile(path) as archive:
        for member in sorted(archive.namelist()):
            if member.endswith("/") or not member.lower().endswith(".txt"):
                continue
            text = archive.read(member).decode("utf-8", errors="replace") if include_content else None
            yield Path(member).name, source_type(member), text


def read_documents(path: Path, limit: int) -> list[dict]:
    # Count only names/types first; do not load the entire corpus into memory.
    counts = Counter(kind for _, kind, _ in iter_entries(path, include_content=False))
    kinds = sorted(counts)
    if not kinds:
        return []
    base, remainder = divmod(limit, len(kinds))
    quotas = {kind: min(counts[kind], base + (index < remainder)) for index, kind in enumerate(kinds)}
    selected: list[dict] = []
    used = Counter()
    for file_name, kind, text in iter_entries(path, include_content=True):
        if used[kind] >= quotas[kind]:
            continue
        if text is None:
            continue
        selected.append(make_document(file_name, kind, text))
        used[kind] += 1
        if len(selected) >= limit:
            break
    return selected


def make_document(file_name: str, kind: str, text: str) -> dict:
    lines = text.splitlines()
    title = lines[0].strip() if lines else file_name
    return {
        "externalId": external_id(file_name),
        "source": "enterprise-rag-bench",
        "sourceType": kind,
        "title": title,
        "content": text,
        "tenantId": "default",
        "department": "engineering",
        "accessLevel": "public",
        "metadata": {"benchmark_file": file_name, "benchmark": "EnterpriseRAG-Bench"},
    }


def post_documents(api_base: str, token: str, documents: list[dict], batch_size: int) -> None:
    url = api_base.rstrip("/") + "/api/enterprise/admin/ingest"
    for start in range(0, len(documents), batch_size):
        payload = json.dumps({"documents": documents[start:start + batch_size]}).encode("utf-8")
        request = urllib.request.Request(url, data=payload, method="POST", headers={
            "Content-Type": "application/json",
            "X-Enterprise-Admin-Token": token,
        })
        try:
            with urllib.request.urlopen(request) as response:
                response.read()
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"ingestion batch failed with HTTP {error.code}") from error
        print(f"submitted {min(start + batch_size, len(documents))}/{len(documents)} documents", flush=True)


def main() -> int:
    args = parse_args()
    documents = read_documents(args.archive, args.max_documents)
    if not documents:
        print("No .txt documents found", file=sys.stderr)
        return 2
    for document in documents:
        document["tenantId"] = args.tenant_id
        document["department"] = args.department
        document["accessLevel"] = args.access_level
    kinds = sorted({document["sourceType"] for document in documents})
    print(f"selected {len(documents)} documents across source types: {', '.join(kinds)}")
    post_documents(args.api_base, args.admin_token, documents, args.batch_size)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
