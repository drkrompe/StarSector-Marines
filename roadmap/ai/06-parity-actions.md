# 06 — Parity Actions

**Stage 1, task 6.** Three actions whose bodies reproduce
`InfantryCombatantBehavior` step-for-step. Together with the
[EliminateEnemies goal](07-parity-goal.md) they form the minimum viable
action library to bake parity.

## Files added

- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/actions/EngageVisibleAction.java`
- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/actions/MoveToFiringPositionAction.java`
- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/actions/MaintainCohesionAction.java`

## EngageVisible

- **Pre:** `HAS_LOS_TO_TARGET` ∧ `IN_RANGE_OF_TARGET`
- **Effect:** `ENEMY_DAMAGED`
- **Cost:** 1
- **execute(member, squad, sim):** primary/secondary fire logic from
  `InfantryCombatantBehavior` — secondary-aim short-circuit, secondary
  trigger (vs `MapTurret`), primary fire + burst queue, post-shot reposition
  roll. Returns RUNNING while firing, SUCCESS once a shot has landed (or
  once cooldown ticks through one full cycle — pick whichever produces the
  least flicker in the replan loop).

## MoveToFiringPosition

- **Pre:** `HAS_TARGET`
- **Effect:** `HAS_LOS_TO_TARGET` ∧ `IN_RANGE_OF_TARGET`
- **Cost:** 2
- **execute(member, squad, sim):** pathfinder + `findFiringPosition` per
  current behavior. Returns RUNNING while pathing, SUCCESS on arrival.

## MaintainCohesion

- **Pre:** none (or `OUTSIDE_COHESION_RADIUS` if we add that predicate)
- **Effect:** `WITHIN_COHESION_RADIUS`
- **Cost:** 3 — higher than engaging-in-place; pulls toward squad ONLY when
  there's no current firing opportunity. The cohesion-override branch in
  `InfantryCombatantBehavior` triggers in the "out of range or no LOS"
  arm, so this action should naturally win the planner search there.
- **execute(member, squad, sim):** call `cohesionOverride` math, path to
  centroid. SUCCESS once within `COHESION_RADIUS`.

## Behavior preserved

- Burst queue + reposition roll on fire — lives on `EngageVisible`.
- Secondary aim short-circuit (timer-locked rocket animation) — lives on
  `EngageVisible`'s execute body, top-of-method like the original.
- Fallback (taking fire) — `FallbackBehavior` still pre-empts at the sim
  dispatch level, just like today. We do NOT subsume it into a GOAP action
  in Stage 1.

## Acceptance

- Each action compiles and its `execute` body is a near-mechanical lift from
  `InfantryCombatantBehavior` (same calls into `TacticalScoring`,
  `GridPathfinder`, `sim.fireShot`, etc.).
- Side-by-side run against the baseline produces visually-indistinguishable
  squad behavior. (Final check happens in [09-parity-validation](09-parity-validation.md).)

## Open questions

- Whether SUCCESS for `EngageVisible` is per-shot or per-burst-cycle. Per-shot
  causes the planner to re-evaluate after every trigger pull, which might be
  fine — replan is cheap when world hasn't changed.
