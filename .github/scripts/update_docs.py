#!/usr/bin/env python3
"""
Update Kreiscraft documentation via an OpenRouter LLM.

Two modes:
  --mode=incremental   Given a git diff of the last push, ask the model to
                       update only the docs affected by the change. One API
                       call per run.
  --mode=rebuild       Rebuild every doc from scratch against the current
                       source of truth. One API call per doc.

Environment variables:
  OPENROUTER_API_KEY   Required.
  OPENROUTER_MODEL     Required (or pass --model). No default.
  OPENROUTER_APP       Optional. Sent as X-Title header (OpenRouter courtesy).
  OPENROUTER_REFERER   Optional. Sent as HTTP-Referer header.

Only files matching README.md or docs/**/*.md may be written. Any other
path emitted by the model is rejected.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path
from typing import Iterable

import requests

# ---------------------------------------------------------------------------
# Paths and constants
# ---------------------------------------------------------------------------

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[1]  # .github/scripts/ -> repo root
DOCS_DIR = REPO_ROOT / "docs"
README = REPO_ROOT / "README.md"
SYSTEM_PROMPT_PATH = SCRIPT_DIR / "system_prompt.md"

OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

# Files considered "source of truth" for rebuild mode. Order matters for context
# priority: earlier entries are more important if the bundle needs trimming.
REBUILD_SOURCE_MANIFEST: tuple[str, ...] = (
    "src/main/resources/plugin.yml",
    "src/main/resources/config.yml",
    "src/main/resources/lang/de.yml",
    "src/main/resources/lang/en.yml",
    "src/main/java/de/kreiscraft/Kreiscraft.java",
    "src/main/java/de/kreiscraft/KreiscraftBootstrap.java",
    "src/main/java/de/kreiscraft/ReloadCommand.java",
    "src/main/java/de/kreiscraft/core/**/*.java",
    "src/main/java/de/kreiscraft/platform/**/*.java",
    "src/main/java/de/kreiscraft/shared/**/*.java",
    "src/main/java/de/kreiscraft/modules/**/*.java",
)

# Regex for parsing model output. Non-greedy body match, dot-matches-newline.
FILE_BLOCK_RE = re.compile(
    r"<<<FILE:\s*(?P<path>[^\n>]+?)\s*>>>\n(?P<body>.*?)\n<<<END>>>",
    re.DOTALL,
)

MAX_INPUT_CHARS_WARN = 800_000  # ~200k tokens, DeepSeek's ballpark ceiling


# ---------------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------------

def log(msg: str) -> None:
    print(f"[update_docs] {msg}", flush=True)


def run_git(*args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(REPO_ROOT), *args],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout


def read_file(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_file(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    # Always terminate with a trailing newline for POSIX-friendliness.
    if not content.endswith("\n"):
        content += "\n"
    path.write_text(content, encoding="utf-8")


# ---------------------------------------------------------------------------
# Gather inputs
# ---------------------------------------------------------------------------

def gather_docs() -> dict[str, str]:
    """Return {repo-relative posix path: content} for all docs plus root README."""
    files: dict[str, str] = {}
    for path in sorted(DOCS_DIR.rglob("*.md")):
        rel = path.relative_to(REPO_ROOT).as_posix()
        files[rel] = read_file(path)
    if README.exists():
        files["README.md"] = read_file(README)
    return files


def get_diff(base: str, head: str) -> str:
    """Return diff between two refs excluding docs, .github, and root README.

    We exclude paths that are outputs of this pipeline so that the model does
    not react to its own previous doc-only commits.
    """
    return run_git(
        "diff",
        f"{base}...{head}",
        "--",
        ".",
        ":(exclude)docs/**",
        ":(exclude).github/**",
        ":(exclude)README.md",
    )


def gather_source_bundle() -> str:
    """Concatenate every source-of-truth file into a single string.

    Used for rebuild mode. Emits a header line before each file so the model
    can see the path.
    """
    seen: set[Path] = set()
    parts: list[str] = []
    for pattern in REBUILD_SOURCE_MANIFEST:
        matches = sorted(REPO_ROOT.glob(pattern))
        # Fallback for non-glob patterns (plain file paths).
        if not matches and "*" not in pattern:
            candidate = REPO_ROOT / pattern
            if candidate.is_file():
                matches = [candidate]
        for match in matches:
            if not match.is_file() or match in seen:
                continue
            seen.add(match)
            rel = match.relative_to(REPO_ROOT).as_posix()
            try:
                content = read_file(match)
            except (UnicodeDecodeError, OSError) as exc:
                log(f"skip {rel}: {exc}")
                continue
            parts.append(f"----- {rel} -----\n{content}")
    bundle = "\n\n".join(parts)
    log(f"source bundle: {len(seen)} files, {len(bundle):,} chars")
    return bundle


# ---------------------------------------------------------------------------
# Build prompts
# ---------------------------------------------------------------------------

def load_system_prompt() -> str:
    if not SYSTEM_PROMPT_PATH.is_file():
        raise SystemExit(f"missing system prompt at {SYSTEM_PROMPT_PATH}")
    return read_file(SYSTEM_PROMPT_PATH)


def format_docs_block(docs: dict[str, str]) -> str:
    parts = []
    for path, content in docs.items():
        parts.append(f"===== BEGIN {path} =====\n{content}\n===== END {path} =====")
    return "\n\n".join(parts)


def build_incremental_user_prompt(diff: str, docs: dict[str, str]) -> str:
    return (
        "MODE: incremental\n\n"
        "A commit has just landed on the main branch. The git diff below shows "
        "every change to source code and configuration since the previous "
        "commit (doc-only paths, workflow files, and the root README are "
        "excluded from the diff so that automated doc updates do not feed back "
        "into themselves).\n\n"
        "Your task: read the diff, decide which documentation files (if any) "
        "must be updated to stay accurate and consistent, and emit the updated "
        "content for those files only, using the <<<FILE:...>>><<<END>>> "
        "format from the system prompt.\n\n"
        "If the diff has no user-visible effect on documentation (internal "
        "refactor, test change, formatting, etc.), emit `NO_CHANGES`.\n\n"
        "===== GIT DIFF =====\n"
        f"{diff}\n"
        "===== END GIT DIFF =====\n\n"
        "===== CURRENT DOCUMENTATION =====\n"
        f"{format_docs_block(docs)}\n"
        "===== END CURRENT DOCUMENTATION ====="
    )


def build_rebuild_user_prompt(
    doc_path: str, doc_content: str, source_bundle: str
) -> str:
    return (
        "MODE: rebuild (per-doc)\n\n"
        f"Regenerate the single documentation file `{doc_path}` from scratch "
        "based on the current source of truth. All source files that define "
        "the plugin's public surface are provided below.\n\n"
        "Constraints:\n"
        "- Preserve the file's Diátaxis quadrant (tutorial / how-to / "
        "reference / explanation). Do not change its purpose.\n"
        "- Preserve the existing top-level structure and headings unless the "
        "source of truth genuinely requires new sections.\n"
        "- Match the voice, terminology, and formatting of the current "
        "document.\n"
        "- Never invent facts. If information is missing from the source, "
        "omit rather than fabricate.\n\n"
        "Emit exactly one file block using the "
        "<<<FILE:...>>><<<END>>> format. If no changes are warranted, emit "
        "`NO_CHANGES`.\n\n"
        f"===== CURRENT CONTENT OF {doc_path} =====\n"
        f"{doc_content}\n"
        f"===== END CURRENT CONTENT =====\n\n"
        "===== SOURCE OF TRUTH =====\n"
        f"{source_bundle}\n"
        "===== END SOURCE OF TRUTH ====="
    )


# ---------------------------------------------------------------------------
# OpenRouter call
# ---------------------------------------------------------------------------

def call_openrouter(
    model: str,
    system_prompt: str,
    user_prompt: str,
    *,
    max_tokens: int,
    temperature: float,
    retries: int = 3,
) -> str:
    api_key = os.environ.get("OPENROUTER_API_KEY")
    if not api_key:
        raise SystemExit("OPENROUTER_API_KEY is not set")

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    if referer := os.environ.get("OPENROUTER_REFERER"):
        headers["HTTP-Referer"] = referer
    if app_name := os.environ.get("OPENROUTER_APP"):
        headers["X-Title"] = app_name

    payload = {
        "model": model,
        "temperature": temperature,
        "max_tokens": max_tokens,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }

    input_chars = len(system_prompt) + len(user_prompt)
    log(
        f"calling {model} — input {input_chars:,} chars, "
        f"max_tokens={max_tokens}, temp={temperature}"
    )
    if input_chars > MAX_INPUT_CHARS_WARN:
        log(f"WARNING: input size {input_chars:,} chars may exceed model context")

    last_error: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            resp = requests.post(
                OPENROUTER_URL,
                headers=headers,
                json=payload,
                timeout=180,
            )
            if resp.status_code == 429 or resp.status_code >= 500:
                raise RuntimeError(
                    f"openrouter {resp.status_code}: {resp.text[:400]}"
                )
            resp.raise_for_status()
            data = resp.json()
            choice = data["choices"][0]
            content = choice["message"]["content"]
            finish_reason = choice.get("finish_reason", "?")
            usage = data.get("usage", {})
            log(
                f"response: finish={finish_reason} "
                f"in={usage.get('prompt_tokens', '?')} "
                f"out={usage.get('completion_tokens', '?')}"
            )
            if finish_reason == "length":
                log("WARNING: response hit max_tokens — output may be truncated")
            return content
        except (requests.RequestException, RuntimeError, KeyError) as exc:
            last_error = exc
            wait = 2 ** attempt
            log(f"attempt {attempt}/{retries} failed: {exc} — retrying in {wait}s")
            time.sleep(wait)

    raise SystemExit(f"openrouter call failed after {retries} attempts: {last_error}")


# ---------------------------------------------------------------------------
# Parse + apply
# ---------------------------------------------------------------------------

ALLOWED_PATH_RE = re.compile(r"^(README\.md|docs/[A-Za-z0-9_./-]+\.md)$")


def parse_response(text: str) -> dict[str, str]:
    """Return {path: content} from the model output.

    Returns an empty dict if the model signalled NO_CHANGES.
    """
    stripped = text.strip()
    if stripped == "NO_CHANGES" or stripped.startswith("NO_CHANGES\n"):
        return {}

    blocks: dict[str, str] = {}
    for match in FILE_BLOCK_RE.finditer(text):
        path = match.group("path").strip()
        body = match.group("body")
        if not ALLOWED_PATH_RE.match(path):
            log(f"REJECTED path outside allow-list: {path!r}")
            continue
        # Guard against traversal
        if ".." in Path(path).parts:
            log(f"REJECTED path traversal: {path!r}")
            continue
        blocks[path] = body

    if not blocks:
        log("no valid <<<FILE:...>>> blocks parsed from response")
        # Dump first 500 chars for post-mortem
        log(f"response preview: {text[:500]!r}")
    return blocks


def apply_updates(updates: dict[str, str], *, dry_run: bool) -> list[str]:
    """Write files to disk. Return list of paths whose content actually changed."""
    changed: list[str] = []
    for rel_path, new_content in updates.items():
        abs_path = REPO_ROOT / rel_path
        old_content = read_file(abs_path) if abs_path.is_file() else ""
        # Normalise trailing newline for comparison
        old_norm = old_content.rstrip("\n")
        new_norm = new_content.rstrip("\n")
        if old_norm == new_norm:
            log(f"unchanged: {rel_path}")
            continue
        changed.append(rel_path)
        if dry_run:
            log(f"[dry-run] would write: {rel_path}")
        else:
            write_file(abs_path, new_content)
            log(f"wrote: {rel_path}")
    return changed


# ---------------------------------------------------------------------------
# Modes
# ---------------------------------------------------------------------------

def run_incremental(args: argparse.Namespace) -> list[str]:
    diff = get_diff(args.base_ref, args.head_ref)
    if not diff.strip():
        log("empty diff — nothing to do")
        return []

    docs = gather_docs()
    log(f"loaded {len(docs)} doc files ({sum(len(v) for v in docs.values()):,} chars)")
    log(f"diff: {len(diff):,} chars")

    system_prompt = load_system_prompt()
    user_prompt = build_incremental_user_prompt(diff, docs)

    response = call_openrouter(
        args.model,
        system_prompt,
        user_prompt,
        max_tokens=args.max_tokens,
        temperature=args.temperature,
    )
    updates = parse_response(response)
    if not updates:
        return []
    return apply_updates(updates, dry_run=args.dry_run)


def run_rebuild(args: argparse.Namespace) -> list[str]:
    docs = gather_docs()
    log(f"rebuilding {len(docs)} doc files")

    source_bundle = gather_source_bundle()
    system_prompt = load_system_prompt()

    all_changed: list[str] = []
    for i, (doc_path, doc_content) in enumerate(docs.items(), 1):
        log(f"--- [{i}/{len(docs)}] {doc_path} ---")
        user_prompt = build_rebuild_user_prompt(doc_path, doc_content, source_bundle)
        response = call_openrouter(
            args.model,
            system_prompt,
            user_prompt,
            max_tokens=args.max_tokens,
            temperature=args.temperature,
        )
        updates = parse_response(response)
        # In rebuild mode the model is instructed to emit exactly one file;
        # accept only the doc it was asked about, log others as anomalies.
        for path in list(updates):
            if path != doc_path:
                log(f"unexpected path in rebuild response: {path} (dropped)")
                del updates[path]
        changed = apply_updates(updates, dry_run=args.dry_run)
        all_changed.extend(changed)
        # Gentle rate-limit courtesy
        time.sleep(1)
    return all_changed


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--mode",
        choices=("incremental", "rebuild"),
        required=True,
    )
    parser.add_argument(
        "--base-ref",
        default="HEAD~1",
        help="Base ref for incremental diff (default: HEAD~1).",
    )
    parser.add_argument(
        "--head-ref",
        default="HEAD",
        help="Head ref for incremental diff (default: HEAD).",
    )
    parser.add_argument(
        "--model",
        default=os.environ.get("OPENROUTER_MODEL"),
        help="OpenRouter model slug. Required via this flag or the OPENROUTER_MODEL env var.",
    )
    parser.add_argument("--max-tokens", type=int, default=8192)
    parser.add_argument("--temperature", type=float, default=0.1)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not args.model:
        parser.error(
            "no model specified: set OPENROUTER_MODEL or pass --model <slug>"
        )
    return args


def main() -> int:
    args = parse_args()
    if args.mode == "incremental":
        changed = run_incremental(args)
    else:
        changed = run_rebuild(args)

    if not changed:
        log("no doc changes")
        return 0

    log(f"changed {len(changed)} file(s):")
    for path in changed:
        log(f"  - {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
