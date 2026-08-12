# Phase 2b — arrival-semantics helpers (runs BEFORE 2a)

Reordered: this sweep lands *before* the mover swap so every commit stays
green. It introduces intent-named helpers implemented on the CURRENT cell-hop
semantics (behavior-identical), so 2a can change movement semantics in one
place instead of at ~45 scattered gates.

## The three intents hiding in `moveProgress == 0f`

The cell-hop mover made "standing on a cell center" a single overloaded
signal. Under continuous motion the three meanings diverge:

| Helper (on `MovementService`) | Today (2b) | After the mover swap (2a) |
|---|---|---|
| `atCell(world, id, cx, cy)` | `cellX==cx && cellY==cy` | `dist(pos, center(cx,cy)) <= ARRIVE_RADIUS` (~0.35f) |
| `settled(world, id)` | `moveProgress == 0f` | not currently moving (no un-exhausted path / zero speed) |
| `mayRepath(world, id)` | `moveProgress == 0f` | `true` — the carrot follower accepts a path swap mid-motion |

`mayRepath` is the trap: gates that exist only because a mid-cell path swap
was illegal must NOT become "only repath when idle" after the swap — a unit
walking a long path would stop re-evaluating. Classifying each site into
arrival vs. stance vs. repath-serialization is the review-worthy judgment of
this phase; implementation is mechanical.

## Sweep rules

- `moveProgress(id) == 0f` (and `!= 0f` negations) → classify: destination
  test → `atCell` (usually already paired with a cell equality); act/stance
  gate → `settled`; repath serialization → `mayRepath`.
- `cellX(m) == postX && cellY(m) == postY` (~12 sites) → `atCell`.
- `FireStance.stanceFor(moveProgress)` → derive from `settled`.
- `setRenderPos(id, cellX(id), cellY(id))` stop-snaps: LEAVE THEM — they are
  load-bearing under the current mover; 2a deletes them with the swap.
- Each changed site gets recorded in the sweep table (file:line, old
  expression, intent chosen, one-line rationale) — reviewed before commit.

## Families (disjoint ownership for fan-out)

A. `battle/infantry/` (postures, patrol, hold, cordon, break-los, prep)
B. `battle/decision/` + `battle/decision/goap/` (flee/fallback, zone actions,
   WorldStateBuilder, HitResponseSystem, TacticalScoring gates)
C. `battle/mech/`, `battle/evacuation/`, `battle/command/`,
   `battle/combat/` (FireStance), `battle/turret/` + equipment-drop pickup

Gate: `gradlew.bat build` green; zero behavior change (helpers replicate old
expressions exactly).
