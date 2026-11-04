#!/usr/bin/env bash
#
# Commit the documentation changes produced by update_docs.py and push them to
# the branch that triggered the workflow.
#
# The docs bot is the only writer of README.md and docs/, but the branch can
# still move while the job is running: a source push may land after checkout,
# or a concurrent run may push its own doc commit first. A bare `git push` then
# dies with:
#
#   ! [rejected]  main -> main (fetch first)
#
# This script keeps that race from failing the workflow:
#   1. commits the generated docs (exits cleanly when nothing changed);
#   2. snapshots the exact content this run produced;
#   3. on each attempt, fetches the branch and, if the remote moved, rebuilds
#      the doc commit on top of the new head by re-applying the snapshot —
#      the generated content wins, since it came from the newest source diff;
#   4. pushes, retrying with backoff until the push fast-forwards.
#
# Usage:
#   push_docs.sh <commit-message> [branch]
#
# Credentials come from the checkout's persisted git config.
# When GITHUB_OUTPUT is set, docs_changed is true after a successful push of
# site documentation. Built-in Actions-token pushes do not trigger workflows,
# so callers use this output to deploy directly instead of waiting for on.push.

set -euo pipefail

commit_message="${1:-docs: sync}"
branch="${2:-main}"
max_attempts="${PUSH_MAX_ATTEMPTS:-5}"

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "docs_changed=false" >> "$GITHUB_OUTPUT"
fi

git config user.name  "kreiscraft-docs-bot"
git config user.email "docs-bot@forgejo.officeryoda.dev"

git add -- README.md docs/
if git diff --cached --quiet -- README.md docs/; then
  echo "no doc changes"
  exit 0
fi
git commit -m "$commit_message"

# Snapshot the files this run changed so they can be replayed over a moved head.
snapshot="$(mktemp -d)"
trap 'rm -rf "$snapshot"' EXIT
mapfile -t changed_files < <(
  git diff-tree --no-commit-id --name-only -r HEAD -- README.md docs/
)
docs_changed=false
for file in "${changed_files[@]}"; do
  if [[ "$file" == docs/* ]]; then
    docs_changed=true
  fi
  mkdir -p "$snapshot/$(dirname "$file")"
  cp "$file" "$snapshot/$file"
done

for ((attempt = 1; attempt <= max_attempts; attempt++)); do
  echo "push attempt $attempt/$max_attempts (branch: $branch)"
  git fetch --quiet origin "$branch"

  if git merge-base --is-ancestor "origin/$branch" HEAD; then
    echo "doc commit already sits on top of origin/$branch"
  else
    remote_head="$(git rev-parse "origin/$branch")"
    echo "origin/$branch moved to ${remote_head:0:9} — rebuilding doc commit on top"
    git reset --hard "$remote_head"
    for file in "${changed_files[@]}"; do
      mkdir -p "$(dirname "$file")"
      cp "$snapshot/$file" "$file"
    done
    git add -- README.md docs/
    if git diff --cached --quiet; then
      echo "generated docs already match origin/$branch — nothing to push"
    else
      git commit -m "$commit_message"
    fi
  fi

  if git push origin "HEAD:refs/heads/$branch"; then
    echo "pushed docs to $branch on attempt $attempt"
    if [ -n "${GITHUB_OUTPUT:-}" ]; then
      echo "docs_changed=$docs_changed" >> "$GITHUB_OUTPUT"
    fi
    exit 0
  fi
  sleep $((attempt * 2))
done

echo "::error::failed to push docs to $branch after $max_attempts attempts"
exit 1
