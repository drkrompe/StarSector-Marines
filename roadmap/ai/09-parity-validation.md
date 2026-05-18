# 09 — Parity Validation

**Stage 1, task 9.** The "did we land it" check before Stage 2 begins.

## What "parity" means

GOAP-driven infantry squads engage, reposition, and cohere
**visually-indistinguishably** from `InfantryCombatantBehavior`-driven squads
in the same scenarios. We do not chase mechanical exact-frame equivalence —
the planner may shift a fire event by one tick, or pick a different but
equally-valid firing position. We chase "a playtester wouldn't notice."

## Scenarios to spot-check

Run a Conquest mission twice — once with `USE_GOAP_INFANTRY = false`, once
with it `true` — and confirm each:

1. **Fireteam approaches an objective.** Squad of 4 deploys from a shuttle,
   pathfinds toward a charge site, opens fire on the garrison when LOS
   acquires.
2. **Squad gets sniped.** One member takes fire from off-screen, triggers
   `FallbackBehavior` (pre-empts GOAP — should still happen, unchanged).
   Remaining members reorient.
3. **Cohesion pull.** Drop one member in the corner of the map far from
   centroid → they path back to the squad before resuming engagement.
4. **Burst fire + reposition.** Watch for the `REPOSITION_CHANCE` sidestepping
   pattern after primary shots.
5. **Secondary against turret.** Squad encounters a `MapTurret` — observe
   rocket aim-and-launch.
6. **All enemies dead.** Squad idles at last position rather than continuing
   to path randomly.

## Debug overlay

Add a toggle key (suggest `G` for GOAP) that draws over each squad's centroid:
- Current goal name
- Current step's action name + count of assigned members
- Plan progress (step `i` of `n`)

Stripped before Stage 2 ships, or kept behind a dev-only flag.

## Acceptance

- All six scenarios produce visually-equivalent behavior under both flag
  states.
- No new logs warnings under GOAP that don't appear in baseline.
- `./gradlew.bat build -x test` clean.
- `USE_GOAP_INFANTRY` defaults to `false` in committed code until Stage 2
  begins; we flip it locally for testing.

## What NOT to do here

- Don't add Stage 2 actions to "improve" parity. The point is identical
  baseline; new tactical actions land in Stage 2 with their own validation.
- Don't refactor `TacticalScoring` for performance. Profiling lives in
  Stage 2 if the planner cadence proves too costly.
