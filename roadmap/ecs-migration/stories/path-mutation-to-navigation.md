# Story: move path mutation off the sim into NavigationService (Service)

## Context

This is the one real **Service** cleanup left — the rest of
`BattleSimulation` is now a thin orchestrator. `setPath` / `clearPath` /
`flushPendingOccupancyDeltas` still have their bodies on the sim
(`BattleSimulation.java:883–918`), but they're pure navigation-domain
behavior: write `u.path` / `u.pathIdx`, then queue an occupancy-map +
destIndex delta. `NavigationService` already owns the occupancy map, the
destIndex, `applyOccupancyDeltaInline`, `pathDestX/Y`, and
`rebuildOccupancyMap` — the state these methods mutate. The logic is
sitting one class away from its data.

## Scope

### Move

- `setPath(Unit, int[])` body → `NavigationService.setPath(...)`.
- `clearPath(Unit)` → `NavigationService.clearPath(...)`.
- `flushPendingOccupancyDeltas()` → already largely a delegate to
  `DamageService`; fold the sim-side wrapper into NavigationService too,
  or leave it as the existing thin delegate.

### Wrinkle — the occupancy-delta sink

`setPath` today queues through `damageService.applyOccupancyDelta` (the
**parallel-safe queued** path), *not* `applyOccupancyDeltaInline`. The
queue lives in `DamageService` deliberately (parallel-dispatch safety
queues are owned there). So `NavigationService.setPath` needs the queued
sink injected — pass a delta sink (e.g. `OccupancyDeltaSink` functional
interface, or the bound `damageService::applyOccupancyDelta` method ref)
into NavigationService at construction. Don't move the queue itself.

### Keep thin sim delegates

~28 AI behaviors call `sim.setPath(...)` / `sim.clearPath(...)`. Keep
`BattleSimulation.setPath/clearPath` as one-line delegates to
`navigation.setPath(...)` — matches the established facade-delegate
pattern (`fireShot`, `applyDamage`, etc.) and keeps consumer churn at
**zero**. This is a body relocation, not a consumer sweep.

## Acceptance

- `gradlew.bat compileJava` clean; all tests pass.
- `setPath`/`clearPath` bodies live on NavigationService; the sim holds
  only delegates.
- The queued (parallel-safe) occupancy-delta path is preserved — no
  switch to the inline path.
- Existing path/occupancy tests still pass (add NavigationService
  coverage if the move exposes an untested seam).

## Priority

Low urgency (no perf win, pure coupling reduction) but the only
remaining Service extraction with real substance. Good standalone
cleanup commit; independent of the SoA promotion stories.
