"""Test push outputs and the deployment path without triggering remote workflows."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path

import yaml


SOURCE_ROOT = Path(os.environ.get("DOCS_PIPELINE_SOURCE_ROOT", Path(__file__).resolve().parents[2]))


def workflow(name):
    return yaml.load((SOURCE_ROOT / ".github/workflows" / name).read_text(), Loader=yaml.BaseLoader)


class DocsPipelineTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(
            prefix="docs-pipeline-",
            dir="/tmp/opencode" if Path("/tmp/opencode").is_dir() else None,
        )
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.remote = self.root / "remote.git"
        self.checkout = self.root / "checkout"
        self.checkout.mkdir()
        self.output = self.root / "outputs"
        self.env = {**os.environ, "GITHUB_OUTPUT": str(self.output), "PUSH_MAX_ATTEMPTS": "1"}
        self.git("init", "--bare", str(self.remote))
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.git("remote", "add", "origin", str(self.remote))
        (self.checkout / "docs").mkdir()
        (self.checkout / "docs/index.md").write_text("Original docs\n")
        (self.checkout / "README.md").write_text("Original README\n")
        self.git("add", ".")
        self.git("commit", "-m", "initial")
        self.git("push", "origin", "main")

    def git(self, *args):
        return subprocess.run(
            ["git", *args], cwd=self.checkout, env=self.env,
            check=True, capture_output=True, text=True,
        ).stdout.strip()

    def push_docs(self, branch="main"):
        return subprocess.run(
            ["bash", str(SOURCE_ROOT / ".github/scripts/push_docs.sh"), "docs: test", branch],
            cwd=self.checkout, env=self.env, capture_output=True, text=True,
        )

    def outputs(self):
        if not self.output.exists():
            return {}
        return dict(line.split("=", 1) for line in self.output.read_text().splitlines())

    def deploy_step(self, name):
        config = workflow(name)
        steps = next(iter(config["jobs"].values()))["steps"]
        return next((step for step in steps if step.get("uses") == "./.github/actions/build-docs"), None)

    def test_bot_push_reaches_deployment_without_a_second_push_event(self):
        (self.checkout / "docs/index.md").write_text("Generated docs\n")
        result = self.push_docs()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.git("show", "origin/main:docs/index.md"), "Generated docs")
        self.assertEqual(self.outputs().get("docs_changed"), "true")
        # No on.push event is delivered for the bot: the generating workflow
        # itself must consume the successful push's output and deploy the checkout.
        for name in ("docs-update.yml", "docs-rebuild.yml"):
            with self.subTest(workflow=name):
                step = self.deploy_step(name)
                self.assertIsNotNone(step, "bot push has no direct deployment path")
                self.assertEqual(step["if"],
                                 "steps.push_docs.outputs.docs_changed == 'true' && github.ref == 'refs/heads/main'")
                self.assertEqual(step["with"]["deploy"], "true")
                steps = next(iter(workflow(name)["jobs"].values()))["steps"]
                push_index = next(i for i, item in enumerate(steps) if item.get("id") == "push_docs")
                self.assertLess(push_index, steps.index(step))

    def test_new_doc_is_deployed(self):
        (self.checkout / "docs/new.md").write_text("New guide\n")
        result = self.push_docs()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.outputs().get("docs_changed"), "true")
        self.assertEqual(self.git("show", "origin/main:docs/new.md"), "New guide")

    def test_readme_only_push_does_not_deploy_site(self):
        (self.checkout / "README.md").write_text("Generated README\n")
        result = self.push_docs()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.outputs().get("docs_changed"), "false")
        self.assertEqual(self.git("show", "origin/main:README.md"), "Generated README")

    def test_no_changes_does_not_deploy(self):
        before = self.git("rev-parse", "HEAD")
        result = self.push_docs()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.outputs().get("docs_changed"), "false")
        self.assertEqual(self.git("rev-parse", "HEAD"), before)

    def test_failed_push_does_not_enable_deploy(self):
        hook = self.remote / "hooks/pre-receive"
        hook.write_text("#!/bin/sh\nexit 1\n")
        hook.chmod(0o755)
        (self.checkout / "docs/index.md").write_text("Generated docs\n")
        result = self.push_docs()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.outputs().get("docs_changed"), "false")
        self.assertEqual(self.git("show", "origin/main:docs/index.md"), "Original docs")

    def test_rebuild_dry_run_cannot_push_or_deploy(self):
        steps = workflow("docs-rebuild.yml")["jobs"]["rebuild"]["steps"]
        push = next(step for step in steps if step.get("id") == "push_docs")
        self.assertEqual(push["if"], "inputs.dry_run != 'true'")
        self.assertIn("steps.push_docs.outputs.docs_changed == 'true'", self.deploy_step("docs-rebuild.yml")["if"])

    def test_all_deployment_paths_share_a_non_cancelling_lock(self):
        locks = [workflow(name)["concurrency"] for name in (
            "docs-update.yml", "docs-rebuild.yml", "docs-deploy.yml",
        )]
        self.assertTrue(all(lock == locks[0] for lock in locks))
        self.assertEqual(locks[0], {"group": "docs-deploy-${{ github.ref }}", "cancel-in-progress": "false"})

    def test_ordinary_pushes_use_shared_action_and_latest_branch(self):
        config = workflow("docs-deploy.yml")
        steps = config["jobs"]["docs"]["steps"]
        self.assertEqual(steps[0]["with"]["ref"],
                         "${{ github.event_name == 'push' && github.ref || github.sha }}")
        self.assertEqual(self.deploy_step("docs-deploy.yml")["with"]["deploy"],
                         "${{ github.event_name == 'push' && github.ref == 'refs/heads/main' }}")
        for event in ("push", "pull_request"):
            self.assertIn(".github/actions/build-docs/**", config["on"][event]["paths"])

    def test_shared_action_validates_before_deployment(self):
        action = yaml.load(
            (SOURCE_ROOT / ".github/actions/build-docs/action.yml").read_text(), Loader=yaml.BaseLoader,
        )
        self.assertEqual(action["inputs"]["deploy"]["default"], "false")
        self.assertEqual(action["runs"]["using"], "composite")
        steps = action["runs"]["steps"]
        self.assertEqual([step["run"] for step in steps if "run" in step][:-1], [
            "bun install --frozen-lockfile", "bun run build", "bun run lint:openapi",
        ])
        deploy = steps[-1]
        self.assertEqual(deploy["if"], "inputs.deploy == 'true'")
        self.assertEqual(deploy["env"], {"SRC": "dist", "SITE": "docs.kreiscraft"})
        for step in steps:
            if "run" in step:
                self.assertEqual(step["working-directory"], "docs-site")
                self.assertEqual(step["shell"], "bash")


if __name__ == "__main__":
    unittest.main()
