"""Offline tests: python -B -m unittest discover -s .github/scripts -p 'test_*.py'."""

import argparse
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import update_docs


class UpdateDocsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(
            prefix="docs-tests-",
            dir="/tmp/opencode" if Path("/tmp/opencode").is_dir() else None,
        )
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name, value in (
            ("REPO_ROOT", self.root),
            ("DOCS_DIR", self.root / "docs"),
            ("README", self.root / "README.md"),
        ):
            patcher = patch.object(update_docs, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)
        self.docs = {
            "docs/reference/example.md": "Heading\nOld row\nRepeated\nRepeated\nTail\n",
            "README.md": "Original README\n",
        }
        for path, content in self.docs.items():
            target = self.root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content)
        self.args = argparse.Namespace(
            model="test-model", max_tokens=4096, temperature=0.1, dry_run=False,
            base_ref="base", head_ref="head",
        )

    def response(self, *edits):
        return json.dumps({"edits": list(edits)})

    def edit(self, old="Old row", new="New row", path="docs/reference/example.md", **extra):
        return {"path": path, "old": old, "new": new, **extra}

    def generate(self, responses):
        with patch.object(update_docs, "call_openrouter", side_effect=responses) as call:
            updates = update_docs.generate_updates(self.args, self.docs, "system", "initial")
        return updates, call

    def assertOriginalsUnchanged(self):
        for path, content in self.docs.items():
            self.assertEqual((self.root / path).read_text(), content)

    def test_targeted_edits_preserve_everything_else(self):
        updates, call = self.generate([self.response(self.edit())])
        self.assertEqual(updates, {
            "docs/reference/example.md": "Heading\nNew row\nRepeated\nRepeated\nTail\n",
        })
        self.assertEqual(call.call_count, 1)
        self.assertOriginalsUnchanged()

    def test_sequential_edits_insert_and_delete(self):
        updates, _ = self.generate([self.response(
            self.edit(new="New row\nInserted"),
            self.edit(old="New row", new="Final row"),
            self.edit(old="Tail\n", new=""),
        )])
        self.assertEqual(updates["docs/reference/example.md"],
                         "Heading\nFinal row\nInserted\nRepeated\nRepeated\n")

    def test_no_changes_has_no_correction(self):
        for response in ("NO_CHANGES", '{"edits": []}'):
            with self.subTest(response=response):
                updates, call = self.generate([response])
                self.assertEqual(updates, {})
                self.assertEqual(call.call_count, 1)

    def test_missing_match_is_corrected_without_repeating_successes(self):
        updates, call = self.generate([
            self.response(self.edit(), self.edit(old="Wrong anchor", new="Updated tail")),
            self.response(self.edit(old="Tail", new="Updated tail", id=1)),
        ])
        self.assertEqual(call.call_count, 2)
        self.assertIn("New row", updates["docs/reference/example.md"])
        self.assertIn("Updated tail", updates["docs/reference/example.md"])
        correction_prompt = call.call_args_list[1].args[2]
        self.assertIn("Wrong anchor", correction_prompt)
        self.assertIn("old matched 0 times", correction_prompt)
        self.assertIn("New row", correction_prompt)
        self.assertNotIn("Original README", correction_prompt)
        self.assertOriginalsUnchanged()

    def test_ambiguous_match_requests_context(self):
        updates, call = self.generate([
            self.response(self.edit(old="Repeated", new="Changed")),
            self.response(self.edit(old="Repeated\nTail", new="Changed\nTail", id=0)),
        ])
        self.assertIn("old matched 2 times", call.call_args_list[1].args[2])
        self.assertEqual(updates["docs/reference/example.md"],
                         "Heading\nOld row\nRepeated\nChanged\nTail\n")

    def test_overlapping_matches_are_ambiguous(self):
        _, failures = update_docs.stage_edits(
            [update_docs.DocEdit("README.md", "aa", "b")], {"README.md": "aaa"},
        )
        self.assertEqual(failures[0].error, "old matched 2 times; expected exactly once")

    def test_correction_matches_successful_staged_content(self):
        updates, _ = self.generate([
            self.response(self.edit(old="Wrong", new="Final"), self.edit()),
            self.response(self.edit(old="New row", new="Final", id=0)),
        ])
        self.assertIn("Final", updates["docs/reference/example.md"])

    def test_failed_correction_stops_after_one_attempt_without_writes(self):
        with patch.object(update_docs, "call_openrouter", side_effect=[
            self.response(self.edit(), self.edit(old="Missing")),
            self.response(self.edit(old="Still missing", id=1)),
        ]) as call:
            with self.assertRaisesRegex(ValueError, "correction failed"):
                update_docs.generate_updates(self.args, self.docs, "system", "initial")
        self.assertEqual(call.call_count, 2)
        self.assertOriginalsUnchanged()

    def test_invalid_correction_cannot_skip_or_redirect_failed_edits(self):
        corrections = [
            "NO_CHANGES", self.response(),
            self.response(self.edit(old="Tail", id=1)),
            self.response(self.edit(old="Original README", path="README.md", id=0)),
            self.response(self.edit(old="Tail", id=0), self.edit(old="Old row", id=1)),
            self.response(self.edit(old="Tail", id=True)),
            json.dumps({"files": [{"path": "README.md", "content": "Rewritten"}]}),
        ]
        for correction in corrections:
            with self.subTest(correction=correction):
                with self.assertRaises(ValueError):
                    self.generate([self.response(self.edit(old="Missing")), correction])
                self.assertOriginalsUnchanged()

    def test_invalid_initial_responses_fail_closed(self):
        invalid = [
            None, "", "NO_CHANGES\nOther output", "```json\n{}\n```",
            '{"edits": [', "null", "[]", "{}", '{"edits": {}}',
            '{"edits": [], "unknown": true}', self.response(self.edit(old="")),
            self.response(self.edit(new=None)), self.response(self.edit(id=0)),
        ]
        for response in invalid:
            with self.subTest(response=response):
                with self.assertRaises(ValueError):
                    self.generate([response])
                self.assertOriginalsUnchanged()

    def test_unsafe_paths_are_rejected(self):
        for path in (
            "build.gradle.kts", "docs/../../README.md", "/docs/example.md",
            "docs//example.md", "docs/./example.md", "README.md\n",
        ):
            with self.subTest(path=path):
                with self.assertRaises(ValueError):
                    update_docs.parse_response(self.response(self.edit(path=path)))

    def test_symlinked_file_and_directory_are_rejected(self):
        (self.root / "docs/link.md").symlink_to(self.root / "README.md")
        (self.root / "docs/linkdir").symlink_to(self.root / "docs/reference")
        for path in ("docs/link.md", "docs/linkdir/example.md"):
            with self.subTest(path=path):
                with self.assertRaisesRegex(ValueError, "symlinked"):
                    update_docs.parse_response(self.response(self.edit(path=path)))

    def test_new_files_and_explicit_rewrites(self):
        updates, _ = self.generate([json.dumps({"files": [
            {"path": "docs/how-to/new.md", "content": "New guide"},
            {"path": "README.md", "content": "Rewritten README\n"},
        ]})])
        changed = update_docs.apply_updates(updates, dry_run=False, originals=self.docs)
        self.assertCountEqual(changed, ["docs/how-to/new.md", "README.md"])
        self.assertEqual((self.root / "docs/how-to/new.md").read_text(), "New guide\n")
        self.assertEqual((self.root / "README.md").read_text(), "Rewritten README\n")

    def test_full_file_writes_cannot_mix_or_duplicate_targets(self):
        file = {"path": "README.md", "content": "Rewritten"}
        for response in (
            {"files": [file, file]},
            {"files": [file], "edits": [self.edit(path="README.md")]},
        ):
            with self.subTest(response=response):
                with self.assertRaises(ValueError):
                    update_docs.parse_response(json.dumps(response))

    def test_existing_file_not_in_snapshot_cannot_be_overwritten(self):
        path = self.root / "docs/new.md"
        path.write_text("Created concurrently\n")
        with self.assertRaisesRegex(ValueError, "not in supplied docs"):
            self.generate([json.dumps({"files": [{"path": "docs/new.md", "content": "New"}]})])
        self.assertEqual(path.read_text(), "Created concurrently\n")

    def test_preflight_rejects_concurrent_changes_before_any_write(self):
        (self.root / "README.md").write_text("Concurrent change\n")
        updates = {"docs/reference/example.md": "Changed doc", "README.md": "Changed README"}
        with self.assertRaisesRegex(ValueError, "changed during generation"):
            update_docs.apply_updates(updates, dry_run=False, originals=self.docs)
        self.assertEqual((self.root / "docs/reference/example.md").read_text(),
                         self.docs["docs/reference/example.md"])
        self.assertEqual((self.root / "README.md").read_text(), "Concurrent change\n")

    def test_dry_run_validates_but_does_not_write(self):
        updates, _ = self.generate([self.response(self.edit())])
        changed = update_docs.apply_updates(updates, dry_run=True, originals=self.docs)
        self.assertEqual(changed, ["docs/reference/example.md"])
        self.assertOriginalsUnchanged()

    def test_both_modes_use_the_edit_pipeline(self):
        for mode in (update_docs.run_incremental, update_docs.run_rebuild):
            with self.subTest(mode=mode.__name__):
                with patch.object(update_docs, "load_system_prompt", return_value="system"), \
                     patch.object(update_docs, "get_diff", return_value="source diff"), \
                     patch.object(update_docs, "gather_source_bundle", return_value="source"), \
                     patch.object(update_docs, "call_openrouter", return_value=self.response(self.edit())):
                    self.args.dry_run = True
                    self.assertEqual(mode(self.args), ["docs/reference/example.md"])
                self.assertOriginalsUnchanged()

    def test_main_returns_failure_without_partial_writes(self):
        with patch.object(update_docs, "parse_args", return_value=argparse.Namespace(
            **vars(self.args), mode="incremental",
        )), patch.object(update_docs, "load_system_prompt", return_value="system"), \
             patch.object(update_docs, "get_diff", return_value="source diff"), \
             patch.object(update_docs, "call_openrouter", side_effect=[
                 self.response(self.edit(), self.edit(old="Missing")),
                 self.response(self.edit(old="Missing", id=1)),
             ]):
            self.assertEqual(update_docs.main(), 1)
        self.assertOriginalsUnchanged()

    def test_truncated_openrouter_response_is_not_accepted_or_retried(self):
        response = Mock(status_code=200)
        response.json.return_value = {
            "choices": [{"message": {"content": self.response(self.edit())},
                         "finish_reason": "length"}],
        }
        with patch.dict("os.environ", {"OPENROUTER_API_KEY": "test"}), \
             patch.object(update_docs.requests, "post", return_value=response) as post:
            with self.assertRaisesRegex(SystemExit, "truncated"):
                update_docs.call_openrouter("test", "system", "prompt", max_tokens=10, temperature=0.1)
        self.assertEqual(post.call_count, 1)


if __name__ == "__main__":
    unittest.main()
