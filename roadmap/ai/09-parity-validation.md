# 09 — Behavior Validation

**Stage 1, task 9.** The "did we land it" check before Stage 2 begins.

> **Reframed from "parity validation" to "behavior validation":** with
> re-imagining permission on the table, the goal is *not* byte-identical
> output against the legacy path. It's "GOAP-driven infantry squads
> engage / move / cohere coherently across the Stage 1 scenarios."

## What "validated" means now

Across the six scenarios below, with `USE_GOAP_INFANTRY = true`, squads:
- Acquire targets and execute postures that make tactical sense.
- Transition between postures (Approach → Engage, Regroup → Approach → Engage)
  on a timescale that doesn't feel laggy (the 2-second periodic replan plus
  death-driven replan triggers should cover it).
- Don't get stuck — no permanent "standing in place" because the plan
  references a dead unit, no mid-aim freeze when a plan flips.
- Mechs continue running through `MechCombatantBehavior` regardless of flag.

If the squad rhythm feels different from the legacy path — different
firing position picks, different reposition cadence — that's fine and
expected. Re-imagining is the whole point.

## Scenarios to spot-check

Conquest is the canonical Stage 1 testbed (rich mix of marine fireteams,
defender garrisons, defender patrols, mechs).

1. **Fireteam approaches an objective.** Squad of 4 deploys from a shuttle,
   pathfinds toward a charge site, opens fire on the garrison when LOS
   acquires. Plan should transition `[Approach, Engage]` → `[Engage]` as the
   squad enters range.
2. **Squad gets sniped.** One member takes fire from off-screen, triggers
   `FallbackBehavior` (pre-empts GOAP — should still happen, unchanged).
   Remaining members reorient; squad replans on the member death.
3. **Cohesion pull.** Drop one member in the corner of the map far from
   centroid → plan inserts `RegroupPosture`; member paths back to the
   squad before the squad resumes engagement.
4. **Burst fire + reposition.** Watch for the `REPOSITION_CHANCE` sidestepping
   pattern after primary shots.
5. **Secondary against turret.** Squad encounters a `MapTurret` — observe
   rocket aim-and-launch. Critical: confirm the **mid-aim short-circuit fix**
   works — the aim animation should complete even if a replan trigger fires
   mid-animation.
6. **All enemies dead.** Squad idles (plan goes null) rather than continuing
   to path randomly.

## Debug overlay — landed as `SquadPlanDebugPanel`

The original doc called for a key-toggled overlay drawn at squad centroids.
Realized instead as a bottom-right HUD panel
([`SquadPlanDebugPanel`](../../src/main/java/com/dillon/starsectormarines/battle/ui/panel/SquadPlanDebugPanel.java))
mirroring the existing `SquadOverviewPanel`. Visible whenever
`USE_GOAP_INFANTRY` is on. One row per squad:

- `SQ-id` colored by faction (blue marine / orange defender)
- Alert-level dot (green / yellow / red)
- Current goal name (or `—` when idle)
- Current posture + step index (`Engage [1/1]`, `Approach [1/2]`, etc.)

Capped at 12 rows so a tactical-map mission with dozens of garrison squads
doesn't paint over the battlefield. The cap can be lifted in Stage 2 or
replaced with a scroll/filter mechanism if we need full visibility.

## Status

**Stage 1 validated** via Conquest mission playtest. Squads engaged
charge-site garrisons, defender patrols closed and fired, mechs operated
independently of GOAP as expected. Behavior reads as "similar to legacy"
which is the correct outcome for Stage 1's tiny action library — Stage 2's
tactical actions (suppress / flank / cover / advance-under-cover) are
where the planner starts producing visibly different combat.

`USE_GOAP_INFANTRY` left in the codebase as a static flag so we can A/B
during Stage 2 development. Default value is now `true`.

## What NOT to do here

- Don't add Stage 2 actions to "improve" parity. The point is identical
  baseline; new tactical actions land in Stage 2 with their own validation.
- Don't refactor `TacticalScoring` for performance. Profiling lives in
  Stage 2 if the planner cadence proves too costly.
