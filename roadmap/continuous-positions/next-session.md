# next-session — continuous-positions

## State of play (2026-08-12)

Migration running in worktree `worktree-continuous-positions`
(`.claude/worktrees/continuous-positions`), based on main @ a27064fc.
Survey phase done (two exhaustive code surveys; findings baked into
`overview.md`). Coordinate convention locked: continuous cell space,
center at `cx + 0.5`, `cellX = floor(x)`.

## Story status

REORDERED: 2b (gate helpers + sweep) runs BEFORE 2a (mover swap) so every
commit stays green — 2a changes each helper's semantics in one place.

- [x] phase-1-storage-flip — landed 0d346838; critique pass clean
- [x] phase-2b-arrival-semantics — helpers aabf8b5f, 50-site sweep 7a095c06
- [ ] phase-2a-continuous-mover — mover swap landed 2d4bad01 (critique pass
      pending); RENDER_POSITION removal (2a-2) in flight
- [ ] phase-2c-spatial-boundaries
- [ ] in-game verification + merge back to main

## Commit chain

- ec60c13a roadmap docs
- 0d346838 phase 1: POSITION int,int -> float,float (behavior-identical)
- aabf8b5f phase 2b groundwork: atCell/settled/mayRepath + FireStance(boolean)
- 7a095c06 phase 2b sweep: 50 gates intent-classified (behavior-identical)
- 2d4bad01 phase 2a-1: continuous carrot-following mover (moveProgress ->
  GAIT_PHASE + LAST_REPATH_TIME; ARRIVE_RADIUS 0.35, LOOKAHEAD 0.45,
  repath throttle 0.35s; 24 stop-snap pairs deleted, idle-pose reset
  centralized in FacingSystem)

## Handoff notes

- Worktree branch must merge back to main at the end (concurrent sessions
  share HEAD on main — never rebase main itself).
- Phase 1 is behavior-identical by construction; if a test fails, the flip
  leaked semantics — fix the flip, don't adjust the test.
- Phase 2a intentionally deletes `moveProgress` accessors so 2b's worklist
  is the resulting compile-error list.
