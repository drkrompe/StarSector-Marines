# Story: move path mutation off the sim into NavigationService (Service)

**Shipped `2f48c36`** (2026-05-28). The last **Service**-extraction with
real substance: `setPath` / `clearPath` bodies moved off
`BattleSimulation` into `NavigationService`, which already owns the
occupancy map + destIndex they mutate. The logic now sits with its data.

## What landed vs. planned

- **As planned:** `NavigationService.setPath(Unit, int[])` and
  `clearPath(Unit)` hold the bodies (write `u.path` / `u.pathIdx`, compute
  the old/new destination, elide self-cell + no-op deltas). The sim keeps
  one-line delegates (`navigation.setPath(...)` / `navigation.clearPath(...)`),
  so the ~28 AI call sites and the `this::clearPath` method-refs handed to
  `EquipmentDropService` / `SquadFallbackSystem` are **unchanged** — body
  relocation, not a consumer sweep.
- **The occupancy-delta-sink wrinkle, as designed:** `setPath` still
  enqueues through the **queued (parallel-safe)** path, not the inline
  applier. The queue stays in `DamageService` (owner of the
  parallel-dispatch safety queues). `NavigationService` gets a
  setter-injected sink — reusing the existing `DamageService.OccupancyApplier`
  functional interface rather than minting a new one — wired at sim
  construction (`navigation.setOccupancyDeltaSink(damageService::applyOccupancyDelta)`).
  Setter, not constructor, because the sim builds the nav service *before*
  the damage service exists (the damage service needs the nav service's
  inline applier method-ref — the dependency runs both ways).
- **`flushPendingOccupancyDeltas` left as-is:** a thin delegate to
  `DamageService`, now `private` on the sim (only `tick()` drains it). Not
  folded into NavigationService — the queue it drains lives in
  DamageService, and "don't move the queue itself" was explicit scope.

## Ride-along sim-surface trim (same commit)

- Deleted the dead `rollFallbackOnHit` sim delegate (zero callers;
  production + tests call `hitResponse.rollFallbackOnHit` directly).
- Privatized the four `flushPending*` methods — documented "public for
  tests" but no test or production site uses the public seam; only
  `tick()` calls them.

## Coverage

No new test needed — the move didn't expose an untested branch. The
inline path (what serial production callers hit) is exercised end-to-end
by `TacticalScoringTest.alliesNearForSpreadCountsCurrentAndPathDest`,
which asserts a `sim.setPath(...)` destination lands in the destIndex /
occupancy-aware scoring. The parallel-vs-inline split is unchanged and
lives in `DamageService`. Full suite green at `2f48c36`.

## Result

With this done, every per-Unit hot-loop primitive worth promoting is off
the POJO and the last substantive Service extraction is complete. The
remaining ECS-migration work is the **terminal**
[`drop-sim-facade-delegators`](../stories/drop-sim-facade-delegators.md)
story (sever the facade-delegate / `*SimContext` seam so consumers depend
on services directly) plus the deferred low-payoff primitives
(`attackCooldown` / `visionRange` / `moveSpeed`, `squadId` / `role`).
