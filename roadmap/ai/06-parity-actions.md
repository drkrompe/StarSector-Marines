# 06 — Squad Postures (Engage / Approach / Regroup)

**Stage 1, task 6.** Three squad postures — together with the
[EliminateEnemies goal](07-parity-goal.md) they form the minimum viable
action library for Stage 1.

**Reframed from "parity actions" to "postures":** these classes describe
what the *squad* is doing, with per-member execution that does the right
thing for each marine. Stage 2 with per-member action assignment will split
the per-member fallback logic out of the postures into proper concurrent
actions; for now Engage carries an out-of-range fallback inline so members
without personal LOS keep advancing while in-range squadmates fire.

## Files added

- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/actions/EngagePosture.java`
- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/actions/ApproachPosture.java`
- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/actions/RegroupPosture.java`

## EngagePosture

- **Pre:** `HAS_LOS_TO_TARGET` ∧ `IN_RANGE_OF_TARGET`
- **Effect:** `ENEMY_DAMAGED`
- **Cost:** 1
- **execute(member, squad, sim):** lifts the in-range fire branch from
  `InfantryCombatantBehavior` — secondary trigger vs `MapTurret`, primary
  fire + burst queue, post-shot reposition roll. Plus the out-of-range
  fallback (cohesion override → path to firing position) for members
  who personally lack LOS while the squad is engaging. Always returns
  RUNNING during normal engagement; FAILURE when target evaporates.
  Plan advancement is driven by squad-level replan triggers
  (alert-level transition, member death, periodic 2-second timer).

## ApproachPosture

- **Pre:** `HAS_TARGET` ∧ `WITHIN_COHESION_RADIUS`
- **Effect:** `HAS_LOS_TO_TARGET` ∧ `IN_RANGE_OF_TARGET`
- **Cost:** 2
- **execute(member, squad, sim):** pathfinder + `findFiringPosition`,
  no firing. The `WITHIN_COHESION_RADIUS` precondition is what makes the
  planner chain `RegroupPosture` ahead of this when the squad is scattered.
  Returns SUCCESS the moment a member arrives in range + LOS, advancing
  the squad plan to Engage.

## RegroupPosture

- **Pre:** none
- **Effect:** `WITHIN_COHESION_RADIUS`
- **Cost:** 3
- **execute(member, squad, sim):** call `cohesionOverride`, path to centroid.
  SUCCESS once `cohesionOverride` returns null (within `COHESION_RADIUS`
  or solo squad).

## Per-tick lifecycle (lives on `GoapInfantryBehavior`, not in postures)

Cooldown ticks (`cooldownTimer`, `secondaryCooldownTimer`) and the
secondary-aim short-circuit run in `GoapInfantryBehavior.prepareForAction`
*before* the posture's `execute` is called. Pulled out of the posture
bodies so:
- A mid-aim marine isn't stuck in animation when the plan flips off
  EngagePosture (real bug we'd have shipped otherwise).
- Cooldowns drain consistently across Engage/Approach/Regroup phases.
- Posture bodies stay focused on intent.

See [`InfantryUnitPrep`](../../src/main/java/com/dillon/starsectormarines/battle/ai/InfantryUnitPrep.java)
for the helper that both `InfantryCombatantBehavior` (legacy path) and
`GoapInfantryBehavior` (new path) call into.

## Acceptance

- Each posture compiles and its `execute` body is a clean lift of the
  relevant `InfantryCombatantBehavior` branch (same `TacticalScoring`,
  `GridPathfinder`, `sim.fireShot` calls).
- Plans fall out as expected: in-range squad → `[Engage]`; out-of-range
  cohered → `[Approach, Engage]`; out-of-range scattered →
  `[Regroup, Approach, Engage]`.

## Open questions

- Whether SUCCESS for `EngagePosture` should fire per-shot or per-burst-cycle
  is moot in Stage 1 because the posture returns RUNNING continuously —
  plan advancement is replan-trigger-driven. Revisit in Stage 2 when
  effect-satisfaction-driven advancement comes in.
