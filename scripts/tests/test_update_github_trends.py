import importlib.util
import unittest
from datetime import date
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "update_github_trends.py"
SPEC = importlib.util.spec_from_file_location("update_github_trends", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


class UpdateGithubTrendsTest(unittest.TestCase):
    def test_parses_and_replaces_snapshot_without_touching_curated_analysis(self):
        weekly_articles = "".join(
            f'''<article class="Box-row"><h2><a href="/owner/repo{i}">repo{i}</a></h2>
            <p class="col-9">AI agent tool {i}</p>
            <a href="/owner/repo{i}/stargazers">{50000 + i:,}</a>
            <span class="d-inline-block float-sm-right">{1000 + i:,} stars this week</span></article>'''
            for i in range(4)
        )
        monthly_articles = weekly_articles.replace("this week", "this month").replace("1,000", "4,000")
        weekly = MODULE.parse_repositories(weekly_articles)
        monthly = MODULE.parse_repositories(monthly_articles)
        self.assertEqual(4, len(weekly))
        self.assertEqual(1000, weekly[0]["period_stars"])
        self.assertEqual(50000, weekly[0]["total_stars"])
        repositories = MODULE.select_repositories(MODULE.merge_periods(weekly, monthly))

        markdown = """---
snapshot_date: 2026-01-01
expires_at: 2026-01-15
---
> 快照日期：2026-01-01
<!-- TREND_SNAPSHOT_START -->old<!-- TREND_SNAPSHOT_END -->
## 人工分析
keep me
"""
        updated = MODULE.update_document(markdown, repositories, date(2026, 8, 25))
        self.assertIn("snapshot_date: 2026-08-25", updated)
        self.assertIn("expires_at: 2026-09-08", updated)
        self.assertIn("owner/repo0", updated)
        self.assertIn("近一周 / 近一月 / 高 Star", updated)
        self.assertIn("周 +1,000 / 月 +4,000", updated)
        self.assertIn("## 人工分析\nkeep me", updated)

    def test_reserves_space_for_independent_high_star_agent_repositories(self):
        recent = [{
            "repo": f"owner/recent{i}",
            "description": "AI agent",
            "period_stars": 1000 - i,
            "total_stars": 10000 + i,
        } for i in range(8)]
        candidates = MODULE.merge_periods(recent, [])
        candidates = MODULE.merge_high_star(candidates, [{
            "repo": "famous/agent-platform",
            "description": "AI agent platform",
            "total_stars": 100000,
        }])

        selected = MODULE.select_repositories(candidates)

        self.assertEqual(8, len(selected))
        self.assertIn("famous/agent-platform", [item["repo"] for item in selected])


if __name__ == "__main__":
    unittest.main()
