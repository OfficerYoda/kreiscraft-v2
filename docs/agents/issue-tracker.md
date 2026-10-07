# Issue tracker: Forgejo

Issues for this repo live in the Forgejo instance at `forgejo.officeryoda.dev`.
Owner: `OfficerYoda` · Repo: `kreiscraft-java`

Use the `forgejo-mcp` MCP tools for all operations — do **not** shell out to `gh` or `glab`.

## Conventions

- **Create an issue**: `tools["forgejo-mcp"].create_issue({ owner, repo, title, body, labels })`
- **Read an issue**: `tools["forgejo-mcp"].get_issue_by_index({ owner, repo, index })` + `tools["forgejo-mcp"].list_issue_comments({ owner, repo, index })`
- **List issues**: `tools["forgejo-mcp"].list_repo_issues({ owner, repo, state: "open", ... })`
- **Apply / remove labels**: `tools["forgejo-mcp"].add_issue_labels(...)` / `tools["forgejo-mcp"].remove_issue_labels(...)`
- **Comment on an issue**: `tools["forgejo-mcp"].create_issue_comment({ owner, repo, index, body })`
- **Update / close an issue**: `tools["forgejo-mcp"].update_issue(...)` · `tools["forgejo-mcp"].issue_state_change({ owner, repo, index, state: "closed" })`
- **Add a blocking dependency**: `tools["forgejo-mcp"].add_issue_dependency({ owner, repo, index, depends_on_index })` — makes `index` depend on `depends_on_index`.
- **Check blockers**: `tools["forgejo-mcp"].list_issue_dependencies({ owner, repo, index })` — returns open issues this one is waiting on.

Always use `owner: "OfficerYoda"` and `repo: "kreiscraft-java"` unless a skill explicitly says otherwise.

## Pull requests as a triage surface

**PRs as a request surface: no.**

## When a skill says "publish to the issue tracker"

Call `tools["forgejo-mcp"].create_issue(...)`.

## When a skill says "fetch the relevant ticket"

Call `tools["forgejo-mcp"].get_issue_by_index(...)` followed by `tools["forgejo-mcp"].list_issue_comments(...)`.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue labelled `wayfinder:map`; tickets are child issues linked via dependencies.

- **Map**: `tools["forgejo-mcp"].create_issue({ ..., labels: ["wayfinder:map"] })` — body holds Notes / Decisions-so-far / Fog.
- **Child ticket**: `tools["forgejo-mcp"].create_issue({ ..., labels: ["wayfinder:<type>"] })` then link it: `tools["forgejo-mcp"].add_issue_dependency({ index: <map>, depends_on_index: <child> })` (the map tracks its children as blockers). Label types: `research`, `prototype`, `grilling`, `task`.
- **Blocking**: `tools["forgejo-mcp"].add_issue_dependency({ index: <child>, depends_on_index: <blocker> })`. A ticket is unblocked when `list_issue_dependencies` returns an empty list.
- **Frontier query**: `tools["forgejo-mcp"].list_repo_issues({ state: "open", label: "wayfinder:task" })`, drop any whose `list_issue_dependencies` result is non-empty; first in list order wins.
- **Claim**: `tools["forgejo-mcp"].update_issue({ index, assignees: ["<your-username>"] })`.
- **Resolve**: comment with the answer, then close the issue, then append a context pointer to the map's Decisions-so-far.
