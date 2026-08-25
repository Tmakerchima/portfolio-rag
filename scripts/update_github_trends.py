#!/usr/bin/env python3
"""Refresh a weekly + monthly GitHub Trending snapshot without rewriting curated analysis."""

from __future__ import annotations

import argparse
import json
import os
import re
import urllib.request
import urllib.parse
from datetime import date, timedelta
from html.parser import HTMLParser
from pathlib import Path


TRENDING_URLS = {
    "weekly": "https://github.com/trending?since=weekly",
    "monthly": "https://github.com/trending?since=monthly",
}
GITHUB_API = "https://api.github.com"
HIGH_STAR_THRESHOLD = 50_000
DEFAULT_TARGET = (
    Path(__file__).resolve().parents[1]
    / "portfolio-rag"
    / "src"
    / "main"
    / "resources"
    / "knowledge"
    / "github-trend.md"
)
RELEVANT = (
    "agent", "rag", "llm", "mcp", "memory", "context", "coding", "plugin",
    "skill", "java", "spring", "security",
)


class TrendingParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.repositories: list[dict[str, str | int]] = []
        self.current: dict[str, str | int] | None = None
        self.in_heading = False
        self.in_description = False
        self.in_period_stars = False
        self.in_total_stars = False
        self.buffer: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        classes = attributes.get("class") or ""
        if tag == "article" and "Box-row" in classes:
            self.current = {"repo": "", "description": "", "period_stars": 0, "total_stars": 0}
        if self.current is None:
            return
        if tag == "h2":
            self.in_heading = True
        elif tag == "a" and self.in_heading and not self.current["repo"]:
            href = attributes.get("href") or ""
            if re.fullmatch(r"/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", href):
                self.current["repo"] = href.strip("/")
        elif tag == "a" and (attributes.get("href") or "").endswith("/stargazers"):
            self.in_total_stars = True
            self.buffer = []
        elif tag == "p" and "col-9" in classes:
            self.in_description = True
            self.buffer = []
        elif tag == "span" and "float-sm-right" in classes:
            self.in_period_stars = True
            self.buffer = []

    def handle_data(self, data: str) -> None:
        if self.current is not None and (self.in_description or self.in_period_stars or self.in_total_stars):
            self.buffer.append(data)

    def handle_endtag(self, tag: str) -> None:
        if self.current is None:
            return
        if tag == "h2":
            self.in_heading = False
        elif tag == "p" and self.in_description:
            self.current["description"] = " ".join("".join(self.buffer).split())
            self.in_description = False
        elif tag == "a" and self.in_total_stars:
            value = "".join(self.buffer).strip().replace(",", "")
            if value.isdigit():
                self.current["total_stars"] = int(value)
            self.in_total_stars = False
        elif tag == "span" and self.in_period_stars:
            value = " ".join("".join(self.buffer).split())
            match = re.search(r"([\d,]+)\s+stars?\s+this\s+(?:week|month)", value, re.IGNORECASE)
            if match:
                self.current["period_stars"] = int(match.group(1).replace(",", ""))
            self.in_period_stars = False
        elif tag == "article":
            if self.current["repo"]:
                self.repositories.append(self.current)
            self.current = None


def parse_repositories(html: str) -> list[dict[str, str | int]]:
    parser = TrendingParser()
    parser.feed(html)
    return parser.repositories


def merge_periods(
    weekly: list[dict[str, str | int]], monthly: list[dict[str, str | int]]
) -> list[dict[str, str | int | list[str]]]:
    merged: dict[str, dict[str, str | int | list[str]]] = {}
    for period, repositories in (("weekly", weekly), ("monthly", monthly)):
        for rank, item in enumerate(repositories, start=1):
            repo = str(item["repo"])
            current = merged.setdefault(repo, {
                "repo": repo,
                "description": str(item["description"]),
                "total_stars": int(item["total_stars"]),
                "weekly_stars": 0,
                "monthly_stars": 0,
                "weekly_rank": 999,
                "monthly_rank": 999,
                "signals": [],
            })
            current["description"] = current["description"] or str(item["description"])
            current["total_stars"] = max(int(current["total_stars"]), int(item["total_stars"]))
            current[f"{period}_stars"] = int(item["period_stars"])
            current[f"{period}_rank"] = rank
            signals = current["signals"]
            assert isinstance(signals, list)
            signals.append(period)
    return list(merged.values())


def merge_high_star(
    repositories: list[dict[str, str | int | list[str]]],
    high_star_repositories: list[dict[str, str | int]],
) -> list[dict[str, str | int | list[str]]]:
    by_repo = {str(item["repo"]): item for item in repositories}
    for rank, item in enumerate(high_star_repositories, start=1):
        repo = str(item["repo"])
        current = by_repo.setdefault(repo, {
            "repo": repo,
            "description": str(item["description"]),
            "total_stars": int(item["total_stars"]),
            "weekly_stars": 0,
            "monthly_stars": 0,
            "weekly_rank": 999,
            "monthly_rank": 999,
            "signals": [],
        })
        current["total_stars"] = max(int(current["total_stars"]), int(item["total_stars"]))
        current["description"] = current["description"] or str(item["description"])
        signals = current["signals"]
        assert isinstance(signals, list)
        if "high_star" not in signals:
            signals.append("high_star")
        current["high_star_rank"] = rank
    return list(by_repo.values())


def select_repositories(
    repositories: list[dict[str, str | int | list[str]]], limit: int = 8
) -> list[dict[str, str | int | list[str]]]:
    relevant = [
        item for item in repositories
        if is_relevant(item)
    ]
    recent = [item for item in relevant if any(
        signal in ("weekly", "monthly") for signal in item["signals"]
    )]
    high_star_only = [item for item in relevant if item["signals"] == ["high_star"]]
    recent.sort(key=lambda item: (
        min(int(item["weekly_rank"]), int(item["monthly_rank"])),
        -int(item["weekly_stars"]),
        -int(item["monthly_stars"]),
        -int(item["total_stars"]),
    ))
    high_star_only.sort(key=lambda item: -int(item["total_stars"]))
    selected = recent[: min(6, limit)] + high_star_only[: min(2, max(0, limit - 6))]
    remaining = sorted(repositories, key=lambda item: (
        0 if item in relevant else 1,
        min(int(item["weekly_rank"]), int(item["monthly_rank"])),
        -int(item["total_stars"]),
    ))
    target_size = min(limit, len(repositories), max(5, len(relevant)))
    for item in remaining:
        if len(selected) >= target_size:
            break
        if item not in selected:
            selected.append(item)
    return selected[:limit]


def is_relevant(item: dict[str, str | int | list[str]]) -> bool:
    text = f"{item['repo']} {item['description']}".lower()
    return any(keyword in text for keyword in RELEVANT) or re.search(r"\bai\b", text) is not None


def enrich_repository(item: dict[str, str | int | list[str]], token: str) -> None:
    request = urllib.request.Request(
        f"{GITHUB_API}/repos/{item['repo']}",
        headers={
            "User-Agent": "portfolio-rag-trend-refresh/1.0",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2026-03-10",
            **({"Authorization": f"Bearer {token}"} if token else {}),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            metadata = json.load(response)
        item["total_stars"] = int(metadata.get("stargazers_count") or item["total_stars"])
        item["description"] = metadata.get("description") or item["description"]
        item["created_at"] = str(metadata.get("created_at") or "")[:10]
        item["pushed_at"] = str(metadata.get("pushed_at") or "")[:10]
    except Exception as error:
        print(f"Warning: unable to enrich {item['repo']}: {error}")


def render_table(repositories: list[dict[str, str | int | list[str]]]) -> str:
    rows = [
        "<!-- TREND_SNAPSHOT_START -->",
        "| 仓库 | 入选信号 | 累计 Stars | 周/月新增 | 简介 |",
        "|---|---|---:|---:|---|",
    ]
    for item in repositories:
        repo = str(item["repo"])
        description = str(item["description"] or "GitHub Trending repository")
        description = description.replace("|", "\\|").replace("\n", " ")
        signals = item["signals"]
        assert isinstance(signals, list)
        signal_names = {"weekly": "近一周", "monthly": "近一月", "high_star": "高 Star"}
        signal_labels = [signal_names[signal] for signal in signals]
        if int(item["total_stars"]) >= HIGH_STAR_THRESHOLD and "高 Star" not in signal_labels:
            signal_labels.append("高 Star")
        total_stars = f"{int(item['total_stars']):,}" if item["total_stars"] else "—"
        period_growth = []
        if int(item["weekly_stars"]):
            period_growth.append(f"周 +{int(item['weekly_stars']):,}")
        if int(item["monthly_stars"]):
            period_growth.append(f"月 +{int(item['monthly_stars']):,}")
        rows.append(
            f"| [{repo}](https://github.com/{repo}) | {' / '.join(signal_labels)} | "
            f"{total_stars} | {' / '.join(period_growth) or '—'} | {description} |"
        )
    rows.append("<!-- TREND_SNAPSHOT_END -->")
    return "\n".join(rows)


def update_document(
    markdown: str, repositories: list[dict[str, str | int | list[str]]], snapshot_date: date
) -> str:
    expires_at = snapshot_date + timedelta(days=14)
    markdown = re.sub(
        r"(?m)^snapshot_date:\s*.*$", f"snapshot_date: {snapshot_date.isoformat()}", markdown, count=1
    )
    markdown = re.sub(
        r"(?m)^expires_at:\s*.*$", f"expires_at: {expires_at.isoformat()}", markdown, count=1
    )
    markdown = re.sub(
        r"(?m)^> 快照日期：.*$", f"> 快照日期：{snapshot_date.isoformat()}", markdown, count=1
    )
    rendered = render_table(repositories)
    updated, replacements = re.subn(
        r"<!-- TREND_SNAPSHOT_START -->.*?<!-- TREND_SNAPSHOT_END -->",
        rendered,
        markdown,
        count=1,
        flags=re.DOTALL,
    )
    if replacements != 1:
        raise ValueError("Trend snapshot markers are missing or duplicated")
    return updated


def fetch_html(period: str) -> str:
    request = urllib.request.Request(
        TRENDING_URLS[period],
        headers={"User-Agent": "portfolio-rag-trend-refresh/1.0", "Accept": "text/html"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8")


def search_high_star_repositories(token: str) -> list[dict[str, str | int]]:
    query = urllib.parse.urlencode({
        "q": f"agent in:name,description stars:>={HIGH_STAR_THRESHOLD} archived:false fork:false",
        "sort": "stars",
        "order": "desc",
        "per_page": 10,
    })
    request = urllib.request.Request(
        f"{GITHUB_API}/search/repositories?{query}",
        headers={
            "User-Agent": "portfolio-rag-trend-refresh/1.0",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2026-03-10",
            **({"Authorization": f"Bearer {token}"} if token else {}),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = json.load(response)
        return [{
            "repo": item["full_name"],
            "description": item.get("description") or "",
            "total_stars": int(item.get("stargazers_count") or 0),
        } for item in payload.get("items", [])]
    except Exception as error:
        print(f"Warning: unable to search high-star Agent repositories: {error}")
        return []


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, help="Use one saved HTML fixture for both periods")
    parser.add_argument("--weekly-input", type=Path)
    parser.add_argument("--monthly-input", type=Path)
    parser.add_argument("--target", type=Path, default=DEFAULT_TARGET)
    parser.add_argument("--date", type=date.fromisoformat, default=date.today())
    parser.add_argument("--dry-run", action="store_true", help="Parse and render without changing the target")
    args = parser.parse_args()

    weekly_input = args.weekly_input or args.input
    monthly_input = args.monthly_input or args.input
    weekly_html = weekly_input.read_text(encoding="utf-8") if weekly_input else fetch_html("weekly")
    monthly_html = monthly_input.read_text(encoding="utf-8") if monthly_input else fetch_html("monthly")
    token = os.environ.get("GITHUB_TOKEN", "")
    candidates = merge_periods(parse_repositories(weekly_html), parse_repositories(monthly_html))
    candidates = merge_high_star(candidates, search_high_star_repositories(token))
    repositories = select_repositories(candidates)
    if len(repositories) < 3:
        raise RuntimeError(f"Only parsed {len(repositories)} repositories; refusing to replace the snapshot")

    for repository in repositories:
        enrich_repository(repository, token)

    original = args.target.read_text(encoding="utf-8")
    updated = update_document(original, repositories, args.date)
    if not args.dry_run:
        args.target.write_text(updated, encoding="utf-8", newline="\n")
    action = "Validated" if args.dry_run else "Updated"
    print(f"{action} {args.target} with {len(repositories)} repositories for {args.date.isoformat()}")


if __name__ == "__main__":
    main()
