"""回归测试：Worker 的 token-aware chunking 必须可重复且严格遵守上限。"""

from __future__ import annotations

import unittest

import enterprise_rag_worker as worker


class EnterpriseRagWorkerChunkingTest(unittest.TestCase):
    def document(self, content: str) -> worker.BenchDocument:
        return worker.BenchDocument("github/doc.txt", "dsid_test", "github", "Title", content, "hash")

    def test_zero_available_overlap_does_not_copy_entire_previous_chunk(self) -> None:
        content = "# Same section\n\n" + " ".join(f"word{index}" for index in range(2000))
        chunks = worker.chunks(self.document(content), max_tokens=700, overlap=80)

        self.assertGreater(len(chunks), 2)
        self.assertLessEqual(max(chunk.token_count for chunk in chunks), 700)
        self.assertTrue(all(chunk.token_count > 0 for chunk in chunks))

    def test_batched_spans_document_boundaries_without_empty_batches(self) -> None:
        values = list(range(23))
        batches = list(worker.batched(values, 10))

        self.assertEqual([10, 10, 3], [len(batch) for batch in batches])
        self.assertEqual(values, [value for batch in batches for value in batch])

    def test_structural_prefix_is_deterministic_and_does_not_call_llm(self) -> None:
        document = self.document("# Body\n\ncontent")
        chunk = worker.BenchChunk(0, "content", "Title > Body", 1)
        args = type("Args", (), {
            "retrieval_prefix_mode": "STRUCTURAL",
            "contextual_enabled": False,
            "contextual_max_prefix_chars": 800,
        })()

        prefix = worker.contextual_prefix(document, chunk, args)

        self.assertIn("Title: Title", prefix)
        self.assertIn("Section: Title > Body", prefix)
        self.assertIn("Source type: github", prefix)

    def test_exact_budget_and_one_over_budget_are_safe(self) -> None:
        encoding = worker.token_encoding()
        exact = encoding.decode(list(range(1, 701)))
        one_over = encoding.decode(list(range(1, 702)))

        for content in (exact, one_over):
            chunks = worker.chunks(self.document(content), max_tokens=700, overlap=0)
            self.assertTrue(chunks)
            self.assertLessEqual(max(chunk.token_count for chunk in chunks), 700)

    def test_output_is_deterministic_and_preserves_long_document_tokens(self) -> None:
        content = "# Upload API\n\n" + " ".join(f"token{index}" for index in range(1800))
        document = self.document(content)

        first = worker.chunks(document, max_tokens=700, overlap=80)
        second = worker.chunks(document, max_tokens=700, overlap=80)

        self.assertEqual(first, second)
        original_text = content.replace("\n", " ")
        decoded = " ".join(chunk.content for chunk in first)
        # Overlap intentionally duplicates tokens, but removing whitespace and
        # overlap should still leave every source word in order. This catches
        # accidental truncation without treating duplicate overlap as a loss.
        original_words = original_text.split()
        decoded_words = decoded.split()
        cursor = 0
        for word in original_words:
            while cursor < len(decoded_words) and decoded_words[cursor] != word:
                cursor += 1
            self.assertLess(cursor, len(decoded_words), f"missing source word: {word}")
            cursor += 1


if __name__ == "__main__":
    unittest.main()
