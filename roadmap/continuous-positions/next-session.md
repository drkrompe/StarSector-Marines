# next-session — continuous-positions

## State of play (2026-08-19) — track complete

The continuous-position migration and its first follow-up, unit-unit
separation steering, are complete. The locked convention (see `overview.md`):
POSITION is continuous floats in cell space, cell `(cx, cy)` spans
`[cx, cx+1)`, center at `cx + 0.5`; `cellX(id) = floor(x)`;
`renderX/renderY` are tolerant center-based reads for render/audio; ints
survive only at genuine grid boundaries (LoS, zones, A*, occupancy, fog).

Separation S1+S2 shipped as `f2fc6ab0` + `acda9fc7`, merged by `9b8eaf2e`.
The pose-twitch playtest correction shipped as `a952a392`. S3 is closed by
`4973fc91`: deterministic one-cell chokepoint flow and coincident eight-runner
melee-pressure regressions both pass without changing the separation
constants. The full 1503-test suite is green. See
`complete/separation-steering.md` for the implementation, critique, and S3
record.

This handoff branch is `codex/continuous-positions-s3`. From the clean main
checkout, merge it with:

    git merge codex/continuous-positions-s3

No continuous-positions story remains active. The candidates at the bottom of
this file are deliberately unscheduled.

## Non-blocking in-game verification queue

These broad migration checks remain useful whenever manual playtesting resumes;
they no longer block the completed separation story:

- Movement smoothness: no per-cell stutter, no walk-pose strobe, mechs
  pivot-then-walk without stutter-stepping, drones orbit smoothly.
- Arrival: units settle exactly on cell centers; cordon holders /
  planters reach their posts (the one-cell-path blocker is fixed +
  regression-tested, but eyes-on confirmation is cheap).
- Combat: tracers originate/terminate on sprites; ranges feel unchanged
  (true-position distance shifts effective range by up to ~0.7 cells at
  boundaries — deliberate); AoE catches big units at their extent.
- Facing: walkers face their travel direction continuously; no south-
  snap flicker; turn-step plays while a mech pivots.

## Shipped story docs

All in `complete/` — each records design + critique findings:
phase-1-storage-flip, phase-2b-arrival-semantics (ran before 2a),
phase-2a-continuous-mover, phase-2c-spatial-boundaries + worklist
(includes follow-up candidates + balance notes), separation-steering.

## Commit chain (worktree branch)

- ec60c13a roadmap docs
- 0d346838 phase 1: POSITION int,int -> float,float (behavior-identical)
- aabf8b5f phase 2b groundwork: atCell/settled/mayRepath + FireStance(boolean)
- 7a095c06 phase 2b sweep: 50 gates intent-classified
- 2d4bad01 phase 2a-1: continuous carrot-following mover
- 89f2e661 phase 2a-2: RENDER_POSITION deleted, render from POSITION
- 2c67bc12 phase 2a-3: critique fixes (one-cell-path blocker,
  velocity-authored presentation, mech hold-heading)
- 24651e4c 2c groundwork: distance primitives widened to float
- a36955e  phase 2c: true-position spatial services + ~90-site sweep
- 9e7c9cf8 2a critique follow-ups (gunfire-alert floor, setPath doc)
- 29c1f774 2c critique fixes (findBestTarget float chain, centroid
  convention closure, round->floor)
- f2fc6ab0 separation S1: relaxation pass + tick slot
- acda9fc7 separation S2: mass, immovables, velocity fold-in + critique fixes
- 9b8eaf2e merge separation S1+S2 to main
- a952a392 S3 pose-twitch deadband from playtest
- 4973fc91 S3 chokepoint + swarm-spread regression closure

## Follow-up candidates (logged, not scheduled)

See `complete/phase-2c-worklist.md` "follow-ups" section: firing-position
ring searches, averageEnemyCell threat centroid, overwatchAxis float
fields, lastSeenEnemy as float pair; plus balance notes (rocket-safety
heuristics vs the per-unit blast expansion, TurretAim range hysteresis).
