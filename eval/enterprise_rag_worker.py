#!/usr/bin/env python3
"""Durable, streaming EnterpriseRAG-Bench worker.

The worker intentionally does not call the public HTTP ingestion endpoint for a
large job. It validates the archive, records a durable SQLite checkpoint, calls
the embedding provider outside the database transaction, and writes only to a
staging corpus. Production activation is a separate, explicit API operation.

Use --dry-run first. A database run requires psycopg[binary] and an explicit
ENTERPRISE_DATABASE_URL; no secrets are written to manifests or logs.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sqlite3
import sys
import time
import urllib.error
import urllib.request
import uuid
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

DSID_RE = re.compile(r"^(dsid_[^_]+)")
DEFAULT_EMBEDDING_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"


@dataclass(frozen=True)
class BenchDocument:
    member: str
    external_id: str
    source_type: str
    title: str
    content: str
    content_hash: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--dataset-name", default="EnterpriseRAG-Bench")
    parser.add_argument("--dataset-version", default="v1.0.0")
    parser.add_argument("--max-documents", type=int, default=0, help="0 means every valid document")
    parser.add_argument("--source-type", default="", help="Only process one source directory")
    parser.add_argument("--chunk-chars", type=int, default=2400)
    parser.add_argument("--overlap-chars", type=int, default=240)
    parser.add_argument("--batch-size", type=int, default=10)
    parser.add_argument("--embedding-url", default=DEFAULT_EMBEDDING_URL)
    parser.add_argument("--embedding-model", default="text-embedding-v3")
    parser.add_argument("--dimensions", type=int, default=1024)
    parser.add_argument("--embedding-api-key", default="")
    parser.add_argument("--database-url", default="")
    parser.add_argument("--corpus-id", default="")
    parser.add_argument("--resume", action="store_true", help="resume the durable checkpoint at --checkpoint")
    parser.add_argument("--checkpoint", type=Path, default=Path("eval/data/EnterpriseRAG-Bench/worker.sqlite3"))
    parser.add_argument("--manifest", type=Path, default=Path("eval/data/EnterpriseRAG-Bench/dataset_manifest.json"))
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def reject_incomplete_archive(path: Path) -> None:
    lowered = path.name.lower()
    if lowered.endswith(".partial") or ".range" in lowered:
        raise ValueError(f"refusing incomplete archive: {path}")
    if path.is_file() and path.suffix.lower() == ".zip":
        with zipfile.ZipFile(path) as archive:
            broken = archive.testzip()
            if broken:
                raise ValueError(f"corrupt ZIP member: {broken}")


def source_type(member: str) -> str:
    parts = [part for part in Path(member).parts if part not in (".", "")]
    return parts[0].lower().replace(" ", "_") if len(parts) > 1 else "unknown"


def external_id(member: str) -> str:
    stem = Path(member).stem
    match = DSID_RE.match(stem)
    return match.group(1) if match else stem


def iter_members(path: Path) -> Iterable[tuple[str, bytes]]:
    if path.is_dir():
        for file in sorted(path.rglob("*.txt")):
            yield str(file.relative_to(path)), file.read_bytes()
        return
    with zipfile.ZipFile(path) as archive:
        for member in sorted(archive.infolist(), key=lambda item: item.filename):
            if member.is_dir() or not member.filename.lower().endswith(".txt"):
                continue
            yield member.filename, archive.read(member)


def normalize(raw: bytes) -> str:
    return raw.decode("utf-8", errors="replace").replace("\ufeff", "").replace("\r\n", "\n").replace("\r", "\n").strip()


def iter_documents(path: Path, source_filter: str, max_documents: int) -> Iterable[BenchDocument]:
    emitted = 0
    for member, raw in iter_members(path):
        kind = source_type(member)
        if source_filter and kind != source_filter:
            continue
        content = normalize(raw)
        if not content:
            continue
        lines = content.splitlines()
        yield BenchDocument(member, external_id(member), kind, lines[0].strip()[:500] if lines else member,
                            content, sha256(content))
        emitted += 1
        if max_documents and emitted >= max_documents:
            return


def sha256(value: str | bytes) -> str:
    payload = value if isinstance(value, bytes) else value.encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(4 * 1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def stable_id(prefix: str, corpus_id: str, value: str) -> str:
    return f"{prefix}-{sha256(corpus_id + ':' + value)[:48]}"


def chunks(document: BenchDocument, max_chars: int, overlap: int) -> list[tuple[int, str]]:
    if max_chars < 400 or overlap < 0 or overlap >= max_chars:
        raise ValueError("chunk size/overlap configuration is invalid")
    paragraphs = [part.strip() for part in re.split(r"\n\s*\n", document.content) if part.strip()]
    pieces: list[str] = []
    current = ""
    for paragraph in paragraphs:
        if len(paragraph) <= max_chars and current and len(current) + len(paragraph) + 2 <= max_chars:
            current += "\n\n" + paragraph
        elif len(paragraph) <= max_chars:
            if current:
                pieces.append(current)
            current = paragraph
        else:
            if current:
                pieces.append(current)
                current = ""
            for start in range(0, len(paragraph), max_chars - overlap):
                pieces.append(paragraph[start:start + max_chars].strip())
    if current:
        pieces.append(current)
    return [(index, piece) for index, piece in enumerate(piece for piece in pieces if piece)]


def contextual_prefix(document: BenchDocument, index: int) -> str:
    return f"source_type={document.source_type}; title={document.title}; external_id={document.external_id}; chunk_index={index}\n\n"


def open_checkpoint(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(path)
    connection.execute("PRAGMA journal_mode=WAL")
    connection.execute("""
        CREATE TABLE IF NOT EXISTS processed (
            external_id TEXT PRIMARY KEY,
            content_hash TEXT NOT NULL,
            status TEXT NOT NULL,
            error_code TEXT NOT NULL DEFAULT '',
            updated_at INTEGER NOT NULL
        )
        """)
    connection.commit()
    return connection


def write_manifest(path: Path, manifest: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def embed(texts: list[str], args: argparse.Namespace) -> list[list[float]]:
    if not args.embedding_api_key:
        raise RuntimeError("embedding API key is required for a non-dry-run")
    payload = json.dumps({"model": args.embedding_model, "input": texts, "dimensions": args.dimensions}).encode()
    request = urllib.request.Request(args.embedding_url, data=payload, method="POST", headers={
        "Authorization": f"Bearer {args.embedding_api_key}", "Content-Type": "application/json"})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                data = json.loads(response.read())
            values = [item["embedding"] for item in sorted(data["data"], key=lambda item: item.get("index", 0))]
            if len(values) != len(texts) or any(len(value) != args.dimensions for value in values):
                raise RuntimeError("embedding provider returned an unexpected batch shape")
            return values
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, KeyError, json.JSONDecodeError) as error:
            if attempt == 4:
                raise RuntimeError(f"embedding request exhausted retries: {type(error).__name__}") from error
            time.sleep(min(30, 2 ** attempt) + (attempt * 0.13))
    raise AssertionError("unreachable")


def create_corpus(connection, args: argparse.Namespace) -> str:
    corpus_id = args.corpus_id or str(uuid.uuid4())
    connection.execute("""
        INSERT INTO enterprise_corpora
            (corpus_id, dataset_name, dataset_version, state, expected_documents,
             embedding_provider, embedding_model, embedding_dimension, chunker_version)
        VALUES (%s, %s, %s, 'STAGING', %s, 'dashscope-compatible', %s, %s, 'worker-v1')
        ON CONFLICT (corpus_id) DO NOTHING
        """, (corpus_id, args.dataset_name, args.dataset_version,
              args.max_documents, args.embedding_model, args.dimensions))
    connection.commit()
    return corpus_id


def create_job(connection, corpus_id: str) -> str:
    job_id = str(uuid.uuid4())
    connection.execute("""
        INSERT INTO enterprise_ingestion_jobs (job_id, corpus_id, status)
        VALUES (%s, %s, 'RUNNING')
        """, (job_id, corpus_id))
    connection.commit()
    return job_id


def update_job(connection, job_id: str, document: BenchDocument, document_count: int, chunk_count: int) -> None:
    connection.execute("""
        UPDATE enterprise_ingestion_jobs
        SET archive_cursor = %s, status = 'RUNNING', documents_processed = %s,
            chunks_processed = %s, updated_at = now()
        WHERE job_id = %s
        """, (document.member, document_count, chunk_count, job_id))
    connection.commit()


def write_document(connection, corpus_id: str, document: BenchDocument, document_chunks: list[tuple[int, str]], vectors: list[list[float]]) -> None:
    document_id = stable_id("doc", corpus_id, document.source_type + ":" + document.external_id)
    document_row = (corpus_id, document_id, document.external_id, document.source_type, document.title,
                    document.content, document.content_hash, json.dumps({"benchmark_file": document.member,
                    "benchmark": "EnterpriseRAG-Bench"}))
    rows = []
    for (index, content), vector in zip(document_chunks, vectors):
        chunk_id = stable_id("chunk", corpus_id, document.external_id + f":{index}:{sha256(content)}")
        embedded_content = contextual_prefix(document, index) + content
        rows.append((corpus_id, chunk_id, document_id, index, embedded_content, sha256(content),
                     max(1, len(embedded_content.split())), json.dumps({"source_type": document.source_type,
                     "title": document.title, "external_id": document.external_id, "chunk_index": index}),
                     "[" + ",".join(str(value) for value in vector) + "]"))
    with connection.transaction():
        connection.execute("TRUNCATE enterprise_documents_stage, enterprise_chunks_stage")
        with connection.cursor() as cursor:
            with cursor.copy("""COPY enterprise_documents_stage
                    (corpus_id, document_id, external_id, source_type, title, content, content_hash, metadata)
                    FROM STDIN""") as copy:
                copy.write_row(document_row)
            with cursor.copy("""COPY enterprise_chunks_stage
                    (corpus_id, chunk_id, document_id, chunk_index, content, content_hash, token_count, metadata, embedding)
                    FROM STDIN""") as copy:
                for row in rows:
                    copy.write_row(row)
        connection.execute("""
            INSERT INTO enterprise_documents
                (corpus_id, document_id, external_id, source, source_type, title, content, content_hash,
                 version, tenant_id, department, access_level, metadata, indexed_at, deleted_at)
            SELECT corpus_id, document_id, external_id, 'enterprise-rag-bench', source_type, title, content, content_hash,
                   1, 'default', 'engineering', 'public', metadata, now(), NULL
            FROM enterprise_documents_stage
            ON CONFLICT (corpus_id, source, external_id) DO UPDATE SET
                source_type = EXCLUDED.source_type, title = EXCLUDED.title, content = EXCLUDED.content,
                content_hash = EXCLUDED.content_hash, version = enterprise_documents.version + 1,
                metadata = EXCLUDED.metadata, indexed_at = now(), deleted_at = NULL
            """)
        connection.execute("DELETE FROM enterprise_chunks WHERE corpus_id = %s AND document_id = %s", (corpus_id, document_id))
        connection.execute("""
            INSERT INTO enterprise_chunks
                (corpus_id, chunk_id, document_id, chunk_index, content, content_hash, token_count, metadata, embedding)
            SELECT corpus_id, chunk_id, document_id, chunk_index, content, content_hash, token_count, metadata, embedding
            FROM enterprise_chunks_stage
            """)


def ensure_staging_tables(connection, dimensions: int) -> None:
    connection.execute("""
        CREATE TEMP TABLE IF NOT EXISTS enterprise_documents_stage (
            corpus_id uuid, document_id varchar(128), external_id varchar(512), source_type varchar(64),
            title text, content text, content_hash char(64), metadata jsonb
        ) ON COMMIT PRESERVE ROWS
        """)
    connection.execute(f"""
        CREATE TEMP TABLE IF NOT EXISTS enterprise_chunks_stage (
            corpus_id uuid, chunk_id varchar(256), document_id varchar(128), chunk_index integer,
            content text, content_hash char(64), token_count integer, metadata jsonb, embedding vector({dimensions})
        ) ON COMMIT PRESERVE ROWS
        """)
    connection.commit()


def finalize_corpus(connection, corpus_id: str, job_id: str) -> None:
    connection.execute("""
        UPDATE enterprise_corpora c SET
            state = 'READY',
            expected_documents = CASE WHEN c.expected_documents = 0
                                      THEN (SELECT count(*) FROM enterprise_documents d WHERE d.corpus_id = c.corpus_id AND d.deleted_at IS NULL)
                                      ELSE c.expected_documents END,
            document_count = (SELECT count(*) FROM enterprise_documents d WHERE d.corpus_id = c.corpus_id AND d.deleted_at IS NULL),
            chunk_count = (SELECT count(*) FROM enterprise_chunks x WHERE x.corpus_id = c.corpus_id),
            embedded_chunk_count = (SELECT count(*) FROM enterprise_chunks x WHERE x.corpus_id = c.corpus_id AND x.embedding IS NOT NULL)
        WHERE c.corpus_id = %s AND c.state IN ('STAGING', 'EMBEDDING', 'INDEXING', 'VALIDATING', 'READY')
        """, (corpus_id,))
    connection.execute("UPDATE enterprise_ingestion_jobs SET status = 'SUCCEEDED', finished_at = now(), updated_at = now() WHERE job_id = %s",
                       (job_id,))
    connection.commit()


def main() -> int:
    args = parse_args()
    args.database_url = args.database_url or os.environ.get("ENTERPRISE_DATABASE_URL", "")
    args.embedding_api_key = args.embedding_api_key or os.environ.get("DASHSCOPE_API_KEY", "")
    if args.max_documents < 0:
        print("--max-documents must be >= 0", file=sys.stderr)
        return 2
    if args.dimensions != 1024:
        print("Current enterprise migration is vector(1024); use --dimensions 1024 or add a matching migration", file=sys.stderr)
        return 2
    reject_incomplete_archive(args.archive)
    checkpoint = open_checkpoint(args.checkpoint)
    manifest = {"dataset_name": args.dataset_name, "dataset_version": args.dataset_version,
                "archive": str(args.archive), "chunker_version": "worker-v1",
                "chunk_chars": args.chunk_chars, "overlap_chars": args.overlap_chars,
                "document_count": 0, "chunk_count": 0, "total_chars": 0, "source_counts": {},
                "sha256": sha256_file(args.archive) if args.archive.is_file() else "directory-manifest-required"}
    connection = None
    corpus_id = args.corpus_id
    job_id = ""
    if not args.dry_run:
        if not args.database_url:
            print("--database-url or ENTERPRISE_DATABASE_URL is required", file=sys.stderr)
            return 2
        try:
            import psycopg
        except ImportError:
            print("Install psycopg[binary] before a database run", file=sys.stderr)
            return 2
        connection = psycopg.connect(args.database_url)
        corpus_id = create_corpus(connection, args)
        job_id = create_job(connection, corpus_id)
        ensure_staging_tables(connection, args.dimensions)

    for document in iter_documents(args.archive, args.source_type, args.max_documents):
        document_chunks = chunks(document, args.chunk_chars, args.overlap_chars)
        manifest["document_count"] += 1
        manifest["chunk_count"] += len(document_chunks)
        manifest["total_chars"] += len(document.content)
        manifest["source_counts"][document.source_type] = manifest["source_counts"].get(document.source_type, 0) + 1
        previous = checkpoint.execute("SELECT status, content_hash FROM processed WHERE external_id = ?", (document.external_id,)).fetchone()
        if previous and previous[0] == "DONE" and previous[1] == document.content_hash:
            continue
        try:
            if not args.dry_run:
                texts = [contextual_prefix(document, index) + content for index, content in document_chunks]
                vectors = []
                for start in range(0, len(texts), max(1, args.batch_size)):
                    vectors.extend(embed(texts[start:start + max(1, args.batch_size)], args))
                write_document(connection, corpus_id, document, document_chunks, vectors)
            checkpoint.execute("INSERT OR REPLACE INTO processed VALUES (?, ?, 'DONE', '', ?)",
                               (document.external_id, document.content_hash, int(time.time())))
            checkpoint.commit()
            if connection:
                update_job(connection, job_id, document, manifest["document_count"], manifest["chunk_count"])
        except Exception as error:  # checkpoint the item, then stop; --resume is explicit and deterministic.
            checkpoint.execute("INSERT OR REPLACE INTO processed VALUES (?, ?, 'FAILED', ?, ?)",
                               (document.external_id, document.content_hash, type(error).__name__, int(time.time())))
            checkpoint.commit()
            print(f"failed external_id={document.external_id} error_code={type(error).__name__}", file=sys.stderr)
            if connection:
                connection.execute("UPDATE enterprise_ingestion_jobs SET status = 'FAILED', failed_count = failed_count + 1, last_error_code = %s, updated_at = now() WHERE job_id = %s",
                                   (type(error).__name__, job_id))
                connection.commit()
                connection.close()
            return 1
        if manifest["document_count"] % 100 == 0:
            write_manifest(args.manifest, manifest)
            print(f"processed {manifest['document_count']} documents", flush=True)
    manifest["status"] = "DRY_RUN_COMPLETE" if args.dry_run else "STAGING_LOAD_COMPLETE"
    manifest["corpus_id"] = corpus_id
    if connection:
        finalize_corpus(connection, corpus_id, job_id)
    write_manifest(args.manifest, manifest)
    if connection:
        connection.close()
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
