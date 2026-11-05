#!/usr/bin/env python3
"""
Update Kreiscraft documentation via an OpenRouter LLM.

Two modes:
  --mode=incremental   Given a git diff of the last push, ask the model to
                       update only the docs affected by the change. One API
                       call per run, plus at most one edit-correction call.
  --mode=rebuild       Rebuild every doc from scratch against the current
                       source of truth. One API call for all docs combined,
                       plus at most one edit-correction call.

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
import json
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

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

# Files considered "source of truth" for rebuild mode.
#
# Only the documented public surface is included — listener and command
# implementation files are excluded because their internal logic does not
# appear in documentation. Keeping them would waste ~50k tokens of context.
#
# Breakdown of what is included and why:
#   Resources         plugin.yml, config.yml, lang files  → commands, permissions, config keys, i18n
#   Entry points      Kreiscraft.java, KreiscraftBootstrap.java, ReloadCommand.java
#   Core SDK          Interfaces and base classes only (not BukkitScheduler, PaperCommandRegistrar, etc.)
#   Shared layer      All files (ChatBridge, Gui, GuiRegistry, UserService, WhitelistService)
#   Module surface    *Module.java, *Config.java, *Data.java, *Event.java per module
#   API module        Everything under modules/api/ (routes, DTOs, verification, ApiServer)
REBUILD_EXPLICIT_FILES: tuple[str, ...] = (
    # ── resources ────────────────────────────────────────────────────────────
    "src/main/resources/plugin.yml",
    "src/main/resources/config.yml",
    "src/main/resources/lang/de.yml",
    "src/main/resources/lang/en.yml",
    # ── plugin entry points ───────────────────────────────────────────────────
    "src/main/java/de/kreiscraft/Kreiscraft.java",
    "src/main/java/de/kreiscraft/KreiscraftBootstrap.java",
    "src/main/java/de/kreiscraft/ReloadCommand.java",
    # ── core SDK: interfaces and base classes only ────────────────────────────
    "src/main/java/de/kreiscraft/core/command/CommandRegistrar.java",
    "src/main/java/de/kreiscraft/core/config/ConfigService.java",
    "src/main/java/de/kreiscraft/core/config/DataFile.java",
    "src/main/java/de/kreiscraft/core/config/YamlConfigService.java",
    "src/main/java/de/kreiscraft/core/event/EventBus.java",
    "src/main/java/de/kreiscraft/core/message/MessageService.java",
    "src/main/java/de/kreiscraft/core/message/PlaceholderResolver.java",
    "src/main/java/de/kreiscraft/core/module/Module.java",
    "src/main/java/de/kreiscraft/core/module/ModuleContext.java",
    "src/main/java/de/kreiscraft/core/module/ModuleException.java",
    "src/main/java/de/kreiscraft/core/module/ModuleRegistry.java",
    "src/main/java/de/kreiscraft/core/module/StartupSummary.java",
    "src/main/java/de/kreiscraft/core/scheduler/Scheduler.java",
    "src/main/java/de/kreiscraft/core/service/ServiceNotFoundException.java",
    "src/main/java/de/kreiscraft/core/service/ServiceRegistry.java",
)

MAX_INPUT_CHARS_WARN = 480_000  # Input-size warning only; context limits vary by model.


# ---------------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------------


def log(msg: str) -> None:
    print(f"[update_docs] {msg}", flush=True)


def run_git(*args: str) -> str:
    try:
        result = subprocess.run(
            ["git", "-C", str(REPO_ROOT), *args],
            check=True,
            capture_output=True,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        stderr = (exc.stderr or "").strip() or "(no stderr)"
        raise SystemExit(
            f"git {args[0]} failed (exit {exc.returncode}):\n{stderr}"
        ) from exc
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
    can see the path. Uses rglob() for recursive patterns to ensure consistent
    behaviour across Python versions.
    """
    seen: set[Path] = set()
    parts: list[str] = []

    def add_file(path: Path) -> None:
        if not path.is_file() or path in seen:
            return
        seen.add(path)
        rel = path.relative_to(REPO_ROOT).as_posix()
        try:
            content = read_file(path)
        except (UnicodeDecodeError, OSError) as exc:
            log(f"skip {rel}: {exc}")
            return
        parts.append(f"----- {rel} -----\n{content}")

    # 1. Explicit single files (resources + entry points + core SDK).
    for rel in REBUILD_EXPLICIT_FILES:
        add_file(REPO_ROOT / rel)

    base = REPO_ROOT / "src/main/java/de/kreiscraft"

    # 2. Shared layer — all files (small, fully documented surface).
    shared_dir = base / "shared"
    if shared_dir.is_dir():
        for path in sorted(shared_dir.rglob("*.java")):
            add_file(path)

    # 3. Module surface — entry points, config, state, custom events only.
    #    Listeners and command builders are excluded (internal, not documented).
    modules_dir = base / "modules"
    if modules_dir.is_dir():
        for suffix in ("*Module.java", "*Config.java", "*Data.java", "*Event.java"):
            for path in sorted(modules_dir.rglob(suffix)):
                add_file(path)
        # API module needs full detail for http-api.md.
        api_dir = modules_dir / "api"
        if api_dir.is_dir():
            for path in sorted(api_dir.rglob("*.java")):
                add_file(path)

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
        "A push has just landed on the main branch. The git diff below shows "
        "changes to source code and configuration across that push "
        "(documentation paths, workflow files, and the root README are "
        "excluded from the diff so that automated doc updates do not feed back "
        "into themselves).\n\n"
        "Your task: read the diff, decide which documentation files (if any) "
        "must be updated to stay accurate and consistent, and emit targeted "
        "JSON edits using the format from the system prompt.\n\n"
        "The supplied documentation is the post-change snapshot and may already "
        "reflect some or all source changes, including documentation updated in "
        "the same push. This is expected. Check each documentation-relevant "
        "change against it, including all affected cross-references. Preserve "
        "accurate content and edit remaining omissions or contradictions. "
        "A fact already stated accurately in the relevant section is covered; "
        "do not repeat it in another sentence or rewrite it to mirror the diff. "
        "An already-updated section does not mean the other sections are complete. "
        "You do not need to determine when or why the documentation was updated. "
        "Once each relevant change has been checked, return the remaining edits "
        "or `NO_CHANGES` if none are needed, even when the diff adds user-visible "
        "features. Internal refactors, test changes, and formatting may also "
        "require no documentation edits.\n\n"
        "===== GIT DIFF =====\n"
        f"{diff}\n"
        "===== END GIT DIFF =====\n\n"
        "===== CURRENT DOCUMENTATION =====\n"
        f"{format_docs_block(docs)}\n"
        "===== END CURRENT DOCUMENTATION ====="
    )


def build_rebuild_user_prompt(docs: dict[str, str], source_bundle: str) -> str:
    doc_list = "\n".join(f"- {p}" for p in docs.keys())
    return (
        "MODE: rebuild (bulk)\n\n"
        "Review every documentation file listed below against the current "
        "source of truth and emit targeted edits only for files that are "
        "inaccurate, incomplete, or out of date. Use the "
        "JSON edit format from the system prompt.\n\n"
        "If a file is already accurate and needs no changes, do not emit it. "
        "Only emit files you are actually changing.\n\n"
        "Constraints:\n"
        "- Preserve each file's Diátaxis quadrant (tutorial / how-to / "
        "reference / explanation). Do not change its purpose.\n"
        "- Preserve each file's overall structure and top-level headings "
        "unless the source of truth genuinely requires new sections.\n"
        "- Match the voice, terminology, and formatting of the current "
        "documents. The docs were written in a single consistent style — "
        "keep it.\n"
        "- Never invent facts. Omit rather than fabricate.\n"
        "- Do not reword, reformat, or polish sections that are already "
        "correct. Minimise diffs.\n"
        "- The current doc content is provided below so you can assess "
        "what needs changing. Use the source of truth for facts.\n\n"
        f"Files to regenerate ({len(docs)}):\n"
        f"{doc_list}\n\n"
        "===== CURRENT DOCUMENTATION =====\n"
        f"{format_docs_block(docs)}\n"
        "===== END CURRENT DOCUMENTATION =====\n\n"
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
                raise RuntimeError(f"openrouter {resp.status_code}: {resp.text[:400]}")
            resp.raise_for_status()
            data = resp.json()
            choice = data["choices"][0]
            content = choice["message"].get("content")
            finish_reason = choice.get("finish_reason", "?")
            usage = data.get("usage", {})
            log(
                f"response: finish={finish_reason} "
                f"in={usage.get('prompt_tokens', '?')} "
                f"out={usage.get('completion_tokens', '?')} "
                f"reasoning={(usage.get('completion_tokens_details') or {}).get('reasoning_tokens', '?')} "
                f"cost={usage.get('cost', '?')} "
                f"id={data.get('id', '?')}"
            )
            if finish_reason == "length":
                raise SystemExit("response hit max_tokens — refusing truncated edits")
            if content is None:
                # Some providers return null content when a reasoning model
                # was cut off mid-reasoning, or when the assistant produced no
                # visible tokens. Treat as an empty string so the caller can
                # log and decide.
                log("WARNING: response content was null")
                content = ""
            return content
        except (requests.RequestException, RuntimeError, KeyError) as exc:
            last_error = exc
            wait = 2**attempt
            log(f"attempt {attempt}/{retries} failed: {exc} — retrying in {wait}s")
            time.sleep(wait)

    raise SystemExit(f"openrouter call failed after {retries} attempts: {last_error}")


# ---------------------------------------------------------------------------
# Parse + apply
# ---------------------------------------------------------------------------

ALLOWED_PATH_RE = re.compile(r"^(README\.md|docs/[A-Za-z0-9_./-]+\.md)$")


@dataclass(frozen=True)
class DocEdit:
    path: str
    old: str | None  # None is a full-file write, reserved for new files/rewrites.
    new: str
    id: int | None = None  # Used only to associate corrections with failed edits.


@dataclass(frozen=True)
class FailedEdit:
    id: int
    edit: DocEdit
    error: str


def validate_path(path: str) -> None:
    if (
        not ALLOWED_PATH_RE.fullmatch(path)
        or ".." in Path(path).parts
        or Path(path).as_posix() != path
    ):
        raise ValueError(f"path outside allow-list: {path!r}")
    target = REPO_ROOT.resolve() / path
    if target.resolve() != target:
        raise ValueError(f"symlinked documentation path: {path!r}")


def parse_response(text: str | None, *, correction: bool = False) -> list[DocEdit]:
    """Parse a complete JSON response; malformed/unsafe output fails closed."""
    if text and text.strip() == "NO_CHANGES" and not correction:
        return []
    if not text or not text.strip():
        raise ValueError("empty model response")
    try:
        payload = json.loads(text)
    except json.JSONDecodeError as exc:
        raise ValueError(f"invalid JSON response: {exc}") from exc
    allowed = {"edits"} if correction else {"edits", "files"}
    if not isinstance(payload, dict) or not payload or set(payload) - allowed:
        raise ValueError("response must contain only edits and optional files")

    edits: list[DocEdit] = []
    for kind in ("edits", "files"):
        items = payload.get(kind, [])
        if not isinstance(items, list):
            raise ValueError(f"{kind} must be an array")
        fields = {"path", "old", "new"} if kind == "edits" else {"path", "content"}
        if correction:
            fields = fields | {"id"}
        for item in items:
            if not isinstance(item, dict) or set(item) != fields:
                raise ValueError(f"invalid {kind} entry: expected {sorted(fields)}")
            if any(not isinstance(item[key], str) for key in fields - {"id"}):
                raise ValueError(f"{kind} fields must be strings")
            validate_path(item["path"])
            if kind == "edits" and not item["old"]:
                raise ValueError("old must be nonempty; anchor insertions to existing text")
            if correction and (type(item["id"]) is not int or item["id"] < 0):
                raise ValueError("correction id must be a nonnegative integer")
            edits.append(DocEdit(
                path=item["path"],
                old=item.get("old"),
                new=item["new"] if kind == "edits" else item["content"],
                id=item.get("id"),
            ))

    written = [edit.path for edit in edits if edit.old is None]
    if any(sum(edit.path == path for edit in edits) != 1 for path in written):
        raise ValueError("full-file writes cannot be repeated or mixed with edits to that file")
    return edits


def stage_edits(
    edits: list[DocEdit], docs: dict[str, str],
) -> tuple[dict[str, str], list[FailedEdit]]:
    """Apply exact, unique replacements sequentially in memory, never on disk."""
    staged = docs.copy()
    failures: list[FailedEdit] = []
    for index, edit in enumerate(edits):
        validate_path(edit.path)
        if edit.old is None:
            if edit.path not in docs and (REPO_ROOT / edit.path).exists():
                raise ValueError(f"full-file target not in supplied docs: {edit.path}")
            staged[edit.path] = edit.new
            continue
        current = staged.get(edit.path)
        if current is None:
            error = "document is missing; replacements require an existing document"
        else:
            # Count overlapping occurrences too: any ambiguous anchor is rejected.
            count = sum(1 for _ in re.finditer(f"(?={re.escape(edit.old)})", current))
            if count == 1:
                staged[edit.path] = current.replace(edit.old, edit.new, 1)
                continue
            error = f"old matched {count} times; expected exactly once"
        failure = FailedEdit(edit.id if edit.id is not None else index, edit, error)
        log(f"edit {failure.id} failed in {edit.path}: {error}")
        failures.append(failure)
    return staged, failures


def build_correction_prompt(failures: list[FailedEdit], staged: dict[str, str]) -> str:
    failed = [
        {"id": failure.id, "path": failure.edit.path, "old": failure.edit.old,
         "new": failure.edit.new, "error": failure.error}
        for failure in failures
    ]
    relevant = {failure.edit.path: staged.get(failure.edit.path, "") for failure in failures}
    return (
        "MODE: correct failed edits\n\n"
        "Successful edits are already applied in memory. Repair only the failed "
        "edits below, preserving their intended changes. Return JSON with an edits "
        "array containing exactly one replacement per failed id, in the listed order. "
        "Each entry must contain id, path, old, new. Keep each id and path unchanged. "
        "Copy old exactly from the CURRENT staged documents, adding surrounding "
        "text if the previous anchor was ambiguous. Apply corrections sequentially. "
        "Return replacements only, not full files or NO_CHANGES.\n\n"
        f"===== FAILED EDITS =====\n{json.dumps(failed, ensure_ascii=False)}\n\n"
        f"===== CURRENT STAGED DOCUMENTS =====\n{format_docs_block(relevant)}"
    )


def generate_updates(
    args: argparse.Namespace, docs: dict[str, str], system_prompt: str, user_prompt: str,
) -> dict[str, str]:
    response = call_openrouter(
        args.model, system_prompt, user_prompt,
        max_tokens=args.max_tokens, temperature=args.temperature,
    )
    staged, failures = stage_edits(parse_response(response), docs)
    if failures:
        log(f"requesting one correction attempt for {len(failures)} failed edit(s)")
        response = call_openrouter(
            args.model, system_prompt, build_correction_prompt(failures, staged),
            max_tokens=args.max_tokens, temperature=args.temperature,
        )
        corrected = parse_response(response, correction=True)
        if [(edit.id, edit.path) for edit in corrected] != [
            (failure.id, failure.edit.path) for failure in failures
        ]:
            raise ValueError("correction must cover exactly the failed ids/paths in order")
        staged, remaining = stage_edits(corrected, staged)
        if remaining:
            raise ValueError("edit correction failed; no documentation will be written")
    return {path: content for path, content in staged.items() if docs.get(path) != content}


def apply_updates(
    updates: dict[str, str], *, dry_run: bool, originals: dict[str, str],
) -> list[str]:
    """Write files to disk. Return list of paths whose content actually changed."""
    # Preflight every target before writing, including concurrent modifications.
    for rel_path in updates:
        validate_path(rel_path)
        path = REPO_ROOT / rel_path
        current = read_file(path) if path.is_file() else None
        if current != originals.get(rel_path):
            raise ValueError(f"documentation changed during generation: {rel_path}")
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

    updates = generate_updates(args, docs, system_prompt, user_prompt)
    if not updates:
        return []
    return apply_updates(updates, dry_run=args.dry_run, originals=docs)


def run_rebuild(args: argparse.Namespace) -> list[str]:
    docs = gather_docs()
    log(f"reviewing {len(docs)} doc files in one call (plus one correction if needed)")

    source_bundle = gather_source_bundle()
    system_prompt = load_system_prompt()
    user_prompt = build_rebuild_user_prompt(docs, source_bundle)

    updates = generate_updates(args, docs, system_prompt, user_prompt)
    if not updates:
        return []
    # Log a summary of what the model decided to skip.
    skipped = [p for p in docs if p not in updates]
    if skipped:
        log(f"model skipped {len(skipped)} unchanged doc(s):")
        for path in skipped:
            log(f"  - {path}")
    # New documentation files are allowed, but noteworthy in a full review.
    unexpected = [p for p in updates if p not in docs]
    if unexpected:
        log(f"WARNING: model emitted {len(unexpected)} unexpected doc path(s):")
        for path in unexpected:
            log(f"  - {path}")
    return apply_updates(updates, dry_run=args.dry_run, originals=docs)


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
    parser.add_argument(
        "--max-tokens",
        type=int,
        default=131072,
        help="Max output tokens including reasoning (default: 131072).",
    )
    parser.add_argument("--temperature", type=float, default=0.1)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not args.model:
        parser.error("no model specified: set OPENROUTER_MODEL or pass --model <slug>")
    if args.max_tokens <= 0:
        parser.error("--max-tokens must be positive")
    return args


def main() -> int:
    args = parse_args()
    try:
        if args.mode == "incremental":
            changed = run_incremental(args)
        else:
            changed = run_rebuild(args)
    except ValueError as exc:
        log(f"ERROR: {exc}")
        return 1

    if not changed:
        log("no doc changes")
        return 0

    log(f"changed {len(changed)} file(s):")
    for path in changed:
        log(f"  - {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
