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
from collections import defaultdict
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


def read_documents(path: Path) -> Iterable[dict]:
    if path.is_dir():
        for file in sorted(path.rglob("*.txt")):
            yield make_document(file.name, source_type(str(file.relative_to(path))), file.read_text(encoding="utf-8"))
        return
    with zipfile.ZipFile(path) as archive:
        for member in sorted(archive.namelist()):
            if member.endswith("/") or not member.lower().endswith(".txt"):
                continue
            yield make_document(Path(member).name, source_type(member), archive.read(member).decode("utf-8", errors="replace"))


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


def stratified_sample(documents: Iterable[dict], limit: int) -> list[dict]:
    buckets: dict[str, list[dict]] = defaultdict(list)
    for document in documents:
        buckets[document["sourceType"]].append(document)
    result: list[dict] = []
    keys = sorted(buckets)
    index = 0
    while len(result) < limit and keys:
        key = keys[index % len(keys)]
        if buckets[key]:
            result.append(buckets[key].pop(0))
        else:
            keys.remove(key)
            index -= 1
        index += 1
    return result


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
    documents = list(read_documents(args.archive))
    if not documents:
        print("No .txt documents found", file=sys.stderr)
        return 2
    selected = stratified_sample(documents, min(args.max_documents, len(documents)))
    for document in selected:
        document["tenantId"] = args.tenant_id
        document["department"] = args.department
        document["accessLevel"] = args.access_level
    kinds = sorted({document["sourceType"] for document in selected})
    print(f"selected {len(selected)} documents across source types: {', '.join(kinds)}")
    post_documents(args.api_base, args.admin_token, selected, args.batch_size)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
