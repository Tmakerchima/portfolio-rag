import json
import tempfile
import unittest
from pathlib import Path

import filter_questions


class FilterQuestionsTest(unittest.TestCase):
    def test_classifies_full_partial_and_unsupported_questions(self) -> None:
        docs = {"a", "b"}
        self.assertEqual("fully_supported", filter_questions.classify({"expected_doc_ids": ["a"]}, docs))
        self.assertEqual("partially_supported", filter_questions.classify({"expected_doc_ids": ["a", "c"]}, docs))
        self.assertEqual("unsupported", filter_questions.classify({"expected_doc_ids": ["c"]}, docs))
        self.assertEqual("unsupported", filter_questions.classify({"expected_doc_ids": []}, docs))

    def test_reads_jsonl_external_ids(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "ids.jsonl"
            path.write_text("a\n{\"external_id\":\"b\"}\n[\"c\"]\n", encoding="utf-8")
            self.assertEqual({"a", "b", "c"}, filter_questions.load_document_ids(path))


if __name__ == "__main__":
    unittest.main()
