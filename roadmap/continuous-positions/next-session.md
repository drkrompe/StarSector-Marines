# next-session — continuous-positions

## State of play (2026-08-12)

Migration running in worktree `worktree-continuous-positions`
(`.claude/worktrees/continuous-positions`), based on main @ a27064fc.
Survey phase done (two exhaustive code surveys; findings baked into
`overview.md`). Coordinate convention locked: continuous cell space,
center at `cx + 0.5`, `cellX = floor(x)`.

## Story status

- [ ] phase-1-storage-flip — IN PROGRESS
- [ ] phase-2a-continuous-mover
- [ ] phase-2b-arrival-semantics
- [ ] phase-2c-spatial-boundaries
- [ ] in-game verification + merge back to main

## Commit chain

(none yet — docs commit first)

## Handoff notes

- Worktree branch must merge back to main at the end (concurrent sessions
  share HEAD on main — never rebase main itself).
- Phase 1 is behavior-identical by construction; if a test fails, the flip
  leaked semantics — fix the flip, don't adjust the test.
- Phase 2a intentionally deletes `moveProgress` accessors so 2b's worklist
  is the resulting compile-error list.
