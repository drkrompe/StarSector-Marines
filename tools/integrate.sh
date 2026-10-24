#!/usr/bin/env bash
# CLAUDE.md's integration step for one session branch, with a hard stop on any failure.
#
#   tools/integrate.sh session/<name> [repo-path]
#
# From the main workspace: require `main` checked out and clean, fetch origin,
# fast-forward local main to origin/main, fast-forward-merge the session branch,
# push main to origin, and only then push main to hub (skipped with a message
# when it is unreachable). Never force-pushes, never pushes anything but main.
set -euo pipefail

branch="${1:?usage: integrate.sh <session-branch> [repo-path]}"

fail() { echo "integrate: $*" >&2; exit 1; }
run() { echo "> git $*"; git "$@"; }

if [ -n "${2:-}" ]; then
  repo="$2"
else
  common="$(git rev-parse --path-format=absolute --git-common-dir)"
  repo="$(dirname "$common")"
fi
cd "$repo"

[ "$branch" != main ] || fail "the branch to integrate must not be main"
git rev-parse --verify --quiet "refs/heads/$branch^{commit}" >/dev/null || fail "no local branch '$branch'"

current="$(git rev-parse --abbrev-ref HEAD)"
[ "$current" = main ] || fail "main workspace is on '$current', not main"
dirty="$(git status --porcelain)"
[ -z "$dirty" ] || fail "main workspace is not clean:
$dirty"

run fetch origin
run merge --ff-only origin/main
run merge --ff-only "$branch"

run push origin main

if git ls-remote hub >/dev/null 2>&1; then
  run push hub main
else
  echo "integrate: hub unreachable, skipping the hub push."
fi
echo "integrate: done."
