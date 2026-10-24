# Git workflow: machines, remotes and recovery

Status: ACTIVE

Written: 2026-10-24

The detail behind `CLAUDE.md`'s session worktree workflow: the two machines and
their remotes, integrating by hand when `tools/integrate.ps1` cannot be used,
and recovering from the 2026-10-18 history rewrite. Read it when integration
fails, when working on the Mac, or when touching an old branch.

## Remotes

**GitHub (`origin`) is the source of truth** (owner, 2026-10-18). Every
integration lands on `origin/main` first; a change that is not there is not
integrated. The project is worked on from a Windows workstation and a MacBook
Pro, each with its own clone and its own local `main`.

The Mac also holds a bare mirror, `/Users/dillon/git/starsectormarines.git`,
the remote `hub` in every clone. It is an accelerator, never an authority: a
fast LAN copy for fetching and for setting up a clone quickly. It only ever
receives `main` after `origin` has accepted it, so it equals `origin/main` or
lags it. Never integrate against `hub/main`, never push to `hub` what `origin`
has not taken, and when the two disagree `origin` wins.

- `git fetch hub` before `git fetch origin` is optional and makes a big
  catch-up nearly free; `origin/main` is what you merge.
- On Windows, `hub` is `ssh://dillon@192.168.1.97/Users/dillon/git/starsectormarines.git`
  and the clone sets `core.sshCommand` to
  `C:/Windows/System32/OpenSSH/ssh.exe -o HostKeyAlias=dillmac.local`. The
  `dillmac.local` name resolves only intermittently from Windows, so the remote
  names the Mac's address and the alias keeps the host-key check on the Mac's
  own key. If the address changes, update the URL; never drop the alias.
- On the Mac, `hub` is the local path and `origin` is GitHub over SSH.

## Integrating by hand

`tools/integrate.ps1` (`tools/integrate.sh` on the Mac) is the normal path; it
stops at the first failure. Use the manual steps only when the script cannot
run. From the main workspace, check it is on `main` and clean, then:

```powershell
git merge --ff-only session/<unique-name>
git push origin main
git push hub main
```

Check each exit code. Never chain the push after the merge unconditionally,
and never pipe these commands: sessions that did either pushed other sessions'
commits early after a failed `--ff-only`. If the fast-forward fails or `origin`
rejects the push because `main` moved, go back to the worktree, merge `main`
and `origin/main` again, verify, and retry. Push `hub` only after `origin`
accepted, and skip it when the Mac is unreachable.

## Untracking a file

Untracking a file (`git rm --cached`) deletes it from every other checkout that
merges the commit, the main workspace included. Restore it there with
`git restore --source=<commit before the untrack> --worktree -- <paths>`; the
file is ignored from then on, so nothing is staged.

## The 2026-10-18 history rewrite

History was rewritten to drop rendered output: every commit id changed, and no
tree did. A branch or worktree cut before then is on the old history. Never
merge one into `main`, which would bring every purged render back. To move one
onto `main` once it holds no commits of its own:

1. List the files the reset would delete:
   `git diff --name-only --diff-filter=D HEAD main | git check-ignore --stdin > list`.
2. `git reset --hard main`.
3. `git restore --source=<old HEAD> --worktree --pathspec-from-file=list`. A
   reset deletes renders that were tracked at the old commit even though they
   are ignored now.

Never `git gc` or prune while old worktrees exist; the old objects are their
only recovery source.
