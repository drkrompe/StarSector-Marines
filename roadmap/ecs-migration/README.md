# Battle-tier ECS migration

Long-running refactor arc moving the battle simulation from
god-class-with-context-interfaces toward a Services-and-Systems shape,
with primitive-per-Unit state migrating into SoA (parallel arrays
keyed by dense index) on `UnitRegistry`. Locked-in direction; not a
proposal.

The campaign tier already runs under this shape ([`SoA primitives + behavior
in Systems`](../campaign/architecture.md)); this work brings the battle
tier closer to the same model.

## Why

The legacy `BattleSimulation` was a 3000+ line god class that also
served as a `*SimContext` interface to every weapons / AI / squad
subsystem. Every subsystem reached into it for read access; every
state slice was inline. Hard to reason about who-owns-what, hard to
test in isolation, no path to systematic perf wins.

Two coupled goals:

- **Decompose into Services (state owners, constructor-injected
  dependencies) and Systems (stateless tick consumers).** Drop the
  context-interface pattern in favor of explicit per-class deps.
- **Migrate per-Unit primitives off the `Unit` POJO into SoA storage
  on `UnitRegistry`.** Establishes the seam for cache-friendly hot
  loops, dense iteration, and (eventually) component-storage refactor.

The user explicitly framed this as a "stepping stone toward ECS — not
the destination, but the prerequisite refactor so a real
component-storage move later has somewhere to land."

## Current state (2026-05-22)

### Phase 1 — `UnitRegistry` infrastructure (landed)
Dense `Unit[]` + monotonic `long entityId` + `Long2IntOpenHashMap` +
swap-and-pop release. No generation bits ([`feedback_skip_generation_bits`
memory](../../memory) — IDs never recycle; lazy-validity via
`getOrNull` returning null).

### Phase 2 — Unit-ref fields → long entity ids
All cross-tick targeting fields migrated:

- 2a — `UnitUpdateSystem` dispatch reads from registry's dense array.
- 2b (`14cf2e6`) — `Unit.target` → `long targetId`.
- 2c (`5e06311`) — `Unit.burstTarget` + `Unit.secondaryAimTarget` +
  `MapTurret.burstTarget`.
- 2d (`2afee3d`) — `MountedTurret.target` + `MountedTurret.burstTarget`.

No class holds a live `Unit` ref for cross-tick targeting state.

### Test-helper contract closure (`e7a97fc` + `fffd973` + `1f26de4`)
Production death pairs `hp=0` with `releaseFromRegistry`; tests now do
the same via `TestUnits.kill(sim, unit)`. After the sweep, the defensive
`isAlive()` follow-up on every registry-resolve site became redundant
and was dropped at ~25 sites. Contract: after any tick boundary,
`sim.resolveUnit(id) != null` ⟺ unit alive.

### Phase 3 — first primitive SoA promotions (the actual storage lift)
Pattern locked in:

- `UnitRegistry` owns the canonical SoA array sized to `dense.length`,
  grown together with dense on allocate, swap-and-popped together on
  release.
- `Unit` keeps a transient `local*` field for pre-allocation seed +
  post-release snapshot (so corpses on the legacy units list still
  report sane values).
- `Unit` accessor methods are `final` for HotSpot CHA monomorphism;
  branch on `registry != null` to route between SoA and local.
- `Unit.denseIdx` + `Unit.registry` back-ref updated by allocate +
  swap-and-pop (the swapped tail unit's denseIdx must update too).

Primitives promoted:

- `hp` / `maxHp` — `float[]` (`7972009` + `53ee895`)
- `cellX` / `cellY` — parallel `int[]` (`a78d417` + `9787bd9`)

Layout decision for cellX/cellY: **parallel `int[]` over interleaved
stride-2**. Locks in single-axis kernel friendliness for future
sort/partition/Vector-API code. Sequential dense iteration prefetches
both lines in tandem, so the paired-access cache cost is a wash with
interleaved.

### Phase 3 consumers — hot loops migrating to dense-iter + array reads

- `e78bd25` — `SquadMoraleSystem` (mech-squad inner loop +
  near-miss attribution helper). First demonstration.
- `4edb1f4` — `UnitSpatialIndex.rebuild` + `UnitDestinationSpatialIndex.rebuild`.
  Per-tick rebuild touches every alive unit; bigger payoff per call
  than the morale loops.
- `d2a1cbd` — `DamageResolver.pickPromotionCandidate`. Small but
  exemplary; locks the pattern across the damage path too.

Consumer migration pattern (now consistent across all three):

```java
Unit[] dense = registry.denseArray();
int[] cellX = registry.cellXArray();
int[] cellY = registry.cellYArray();
int liveCount = registry.liveCount();
for (int i = 0; i < liveCount; i++) {
    // dense iteration excludes released slots — no isAlive() filter
    // cellX[i] / cellY[i] read directly from SoA arrays
}
```

## What's NOT in scope yet

- **Spatial index payload shape.** Buckets still hold `ArrayList<Unit>`.
  Migrating to intrusive linked lists with `cellHeads[]` + `next[]`
  arrays (MoonLight engine pattern) would eliminate the Unit-ref
  pointer-chase in `gather()`'s inner loop. Real win, real new
  contract (double-buffer for staleness, or single-buffer with
  in-grid position snapshot). Deferred until N grows OR profiling
  flags `gather()` as hot. See [`spatial-index-options.md`](spatial-index-options.md).
- **Morton-sort the SoA arrays periodically.** Spatial locality of
  storage matches likely access pattern. Real technique, real win for
  large N. Not worth the bookkeeping at our N=200 peak. Same triggers
  as above.
- **`MountedTurret` non-Unit migration.** `MountedTurret` itself isn't
  a Unit (no entityId of its own); only its *targets* are. The class
  still holds Unit-typed `*targetId` fields routing through the
  registry, which is correct. Marked complete.
- **Other primitives** — `cooldownTimer`, `moveProgress`,
  `renderX`/`renderY`, `attackRange`/`attackDamage`/`accuracy`,
  `squadId`, `path`. Candidates listed in next-session.

## Design rules locked in

These are the rules every future SoA promotion has to follow. Stop
and re-check the doc if you find yourself wanting to break one.

1. **Final accessors on `Unit`.** `getFoo() / setFoo()` are
   `public final` to keep HotSpot CHA monomorphic across subclasses
   (Drone, DroneHubUnit, MapTurret).
2. **`local*` transient on Unit.** Pre-allocation seed + post-release
   snapshot only. Never read directly except in registry's allocate /
   release; everything else routes through the accessor. Doc the
   xstream/Serializable caveat at the field site
   ([`Unit.java:118-126`](../../src/main/java/com/dillon/starsectormarines/battle/Unit.java)).
3. **Release snapshots back.** Corpses on the legacy units list still
   need to report sane values for the post-death systems that haven't
   migrated yet (drone-crash sprite, legacy iteration paths).
4. **Tail-swap updates the moved unit's `denseIdx`.** Failing to do
   this is the load-bearing bug — caught by
   `releaseUpdatesDenseIdxOfTheSwappedTailUnit` in
   [`UnitRegistryTest`](../../src/test/java/com/dillon/starsectormarines/battle/unit/UnitRegistryTest.java).
   Every primitive promoted needs an equivalent test.
5. **Parallel arrays, not interleaved.** Default to separate arrays
   per axis (`int[] cellX, int[] cellY`). Pick interleaved only when
   the access pattern is genuinely always-paired AND you've
   surrendered single-axis kernel friendliness intentionally.
6. **Consumer migrations capture array refs once at the top of
   `tick()` or the hot method.** The registry's `denseArray() /
   cellXArray() / hpArray()` may reallocate on growth; safe to alias
   for the duration of a serial phase that doesn't allocate.
7. **Test parity.** New primitive promotion needs three tests on
   `UnitRegistryTest`: allocate-seed, release-snapshot, tail-swap.
   The hp/maxHp + cellX/cellY tests are the template.

## Memory entries to read alongside

- [`battle_services_systems`](../../memory) — Service/System
  decomposition direction, dense-iter ECS seam, registry shape.
- [`feedback_skip_generation_bits`](../../memory) — why no generation
  bits.
- [`feedback_entity_for_loop_endgame`](../../memory) — default to
  ECS shape in battle-tier extractions.

## Files

- [`next-session.md`](next-session.md) — picking up from where today
  left off. Read first if you're starting a fresh session on this.
- [`spatial-index-options.md`](spatial-index-options.md) — design
  notes on the LinkedSpatialGrid port + double-buffer pattern,
  captured for when N grows enough to justify it.
