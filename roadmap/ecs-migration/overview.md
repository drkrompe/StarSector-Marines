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

## Design rules

These are the rules every future SoA promotion has to follow. Stop
and re-check the doc if you find yourself wanting to break one.

1. **Final accessors on `Unit`.** `getFoo() / setFoo()` are
   `public final` to keep HotSpot CHA monomorphic across subclasses
   (Drone, DroneHubUnit, MapTurret).
2. **`local*` transient on Unit.** Pre-allocation seed + post-release
   snapshot only. Never read directly except in registry's allocate /
   release; everything else routes through the accessor. Doc the
   xstream/Serializable caveat at the field site
   ([`Unit.java:118-126`](../../src/main/java/com/dillon/starsectormarines/battle/unit/Unit.java)).
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

## What's NOT in scope yet

- **Spatial index payload shape.** See
  [`spatial-index-options.md`](spatial-index-options.md).
- **Morton-sort the SoA arrays periodically.** Not worth at N=200.
- **`MountedTurret` non-Unit migration.** Already done — targets route
  through registry via `*targetId` fields.

## Naming: defer `entity` until `Unit` *is* an id

A rename — `battle.unit` → `battle.entity`, with `Unit` itself eventually
`Entity` — was prototyped locally and reverted (2026-05-28). The decision:
**rename last, not first.** The name `Entity` (and the `entity` package)
should describe an *achieved* reality, not an aspiration.

Today `Unit` is still a fat POJO whose primitives are being peeled into
SoA arrays one at a time (see the promotions list in
[`next-session.md`](next-session.md)). It is not yet an ECS entity — a
bare id / handle over component storage owned by Systems and Services.
Adopting ECS-entity naming now would overclaim the abstraction and burn
the cleanest name on what is still an archetype object.

**North star:** keep `battle.unit.Unit` until the SoA peel has hollowed it
out enough that the remaining type genuinely *is* an id + thin accessor
shim — at which point a rename to `Entity` documents what it has *become*.
Until then the lever is data-model work (the Services-own-state peel,
perf-gated on hot loops like `UnitUpdateSystem`), not naming.

## Next phase: the component model

The SoA-peel and the [facade-decoupling](stories/drop-sim-facade-delegators.md)
work built the **storage + transform** half of ECS. The next phase closes
the **identity + composition** gap — see [`component-model.md`](component-model.md)
for the north star (engine-vs-game framing, the Artemis gap, the
Valhalla-aligned target) and its two seeded stories:

- [`collapse-unit-handle`](stories/collapse-unit-handle.md) — finish
  hollowing `Unit` to an id handle; retire the `local*` duality; unblock
  the `Entity` rename above.
- [`component-grouping`](stories/component-grouping.md) — group SoA columns
  into named component structs (Valhalla-aligned); model optional
  capabilities (the incoming **vehicle HP / ground+air bodies / mounted
  weapons**) as component *presence* over nullable-field if/else.

These are pulled by the imminent vehicle-entity work — that's the forcing
function that makes the optional-capability explosion concrete.

## Memory entries to read alongside

- [`battle_services_systems`](../../memory) — Service/System
  decomposition direction, dense-iter ECS seam, registry shape.
- [`feedback_skip_generation_bits`](../../memory) — why no generation
  bits.
- [`feedback_entity_for_loop_endgame`](../../memory) — default to
  ECS shape in battle-tier extractions.
