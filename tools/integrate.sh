#!/usr/bin/env bash
# CLAUDE.md's integration step for one session branch, with a hard stop on any failure.
#
#   tools/integrate.sh session/<name> [repo-path]
#
# From the main workspace: require `main` checked out and clean, fetch origin,
# fast-forward local main to origin/main, fast-forward-merge the session branch,
# push main to origin, and only then push main to hub (skipped with a message
# when it is unreachable). Never force-pushes, never pushes anything but main.
#
# One run at a time: it holds a lock directory (integrate.lock.d in the git common dir) for the whole run, waiting up
# to five minutes, and takes over a lock whose owner is no longer running. Each fast-forward moves refs/heads/main
# first, as a compare-and-swap against the commit it read, and only then checks the new tree out, so a run that loses
# a race leaves the workspace untouched (git merge --ff-only checks out first and swaps the ref last).
set -euo pipefail

branch="${1:?usage: integrate.sh <session-branch> [repo-path]}"

fail() { echo "integrate: $*" >&2; exit 1; }
run() { echo "> git $*"; git "$@"; }

# Fast-forward main to $1: the ref first, compared and swapped, then the tree over the clean workspace.
move_main() {
  local old new
  old="$(git rev-parse --verify 'refs/heads/main^{commit}')"
  new="$(git rev-parse --verify "$1^{commit}")"
  if [ "$old" = "$new" ]; then echo "integrate: main is already at $1"; return; fi
  git merge-base --is-ancestor "$old" "$new" || fail "$1 is not a fast-forward of main; merge main into it and rerun"
  run update-ref -m "integrate: fast-forward to $1" refs/heads/main "$new" "$old"
  echo "> git read-tree -u -m $old $new"
  git read-tree -u -m "$old" "$new" \
    || fail "main moved to $new but its tree did not check out; from the main workspace run: git read-tree -u -m $old $new"
}

if [ -n "${2:-}" ]; then
  repo="$2"
else
  common="$(git rev-parse --path-format=absolute --git-common-dir)"
  repo="$(dirname "$common")"
fi
cd "$repo"

lock="$(git rev-parse --path-format=absolute --git-common-dir)/integrate.lock.d"
waited=0
until mkdir "$lock" 2>/dev/null; do
  owner="$(cat "$lock/pid" 2>/dev/null || true)"
  if [ -n "$owner" ] && ! kill -0 "$owner" 2>/dev/null; then
    rm -rf "$lock"
    continue
  fi
  [ "$waited" -lt 300 ] || fail "another integrate still holds $lock after five minutes"
  echo "integrate: another run is integrating; waiting."
  sleep 3
  waited=$((waited + 3))
done
echo $$ > "$lock/pid"
trap 'rm -rf "$lock"' EXIT

[ "$branch" != main ] || fail "the branch to integrate must not be main"
git rev-parse --verify --quiet "refs/heads/$branch^{commit}" >/dev/null || fail "no local branch '$branch'"

current="$(git rev-parse --abbrev-ref HEAD)"
[ "$current" = main ] || fail "main workspace is on '$current', not main"
dirty="$(git status --porcelain)"
[ -z "$dirty" ] || fail "main workspace is not clean:
$dirty"

run fetch origin
move_main origin/main
move_main "refs/heads/$branch"

run push origin main

if git ls-remote hub >/dev/null 2>&1; then
  run push hub main
else
  echo "integrate: hub unreachable, skipping the hub push."
fi
echo "integrate: done."
