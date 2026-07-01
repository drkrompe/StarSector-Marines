# Systems → columns (the systems half of the ECS migration)

> **Status: CLOSED at its positive-value terminus (2026-07-01).** Phase 0 measured
> the whole epic at ~0.02% of a 30 Hz frame; Slice 1 (occupancy) collected the one
> genuine win; the remaining candidates are lateral (they *move* hashmap probes, not
> remove them) or Phase-0-parked. The literal headline goal — column-walk the
> `UnitUpdateSystem` combatant loop — is parked by the epic's own Phase-0 gate. See
> **§ Terminus (2026-07-01)** below for the code-grounded walk of why each remaining
> slice doesn't pay. Read [`overview.md`](../overview.md) for the SoA design rules and
> [`archetype-storage.md`](../archetype-storage.md) for the engine the game systems
> consume.
>
> ## Terminus (2026-07-01)
>
> Picked up to continue after the entity-field migration landed (the doc's "second
> finding" said the spatial/vision slices would "fall out for free" once it did).
> Walking the three remaining candidates against the actual code falsified that:
>
> - **Two doc premises were inaccurate.** `faction`/`type` are *already* dual-homed —
>   `public final` fields on `Entity` **and** the `IDENTITY` column (id 0, OBJECT/OBJECT,
>   persists alive→dead). So nothing was blocked on an "identity migration"; and the
>   remaining slices don't fall out for free.
> - **Spatial-index rebuilds (`UnitSpatialIndex`/`UnitDestinationSpatialIndex`) — lateral,
>   not a win.** The buckets already denormalize `cellX/cellY` (gather's distance test is
>   probe-free) and store `Entity` refs, so gather callers read `.faction` off the ref for
>   free. Column-walking `rebuild` removes 2 cell-probes/unit but the bucket still needs a
>   unit payload: either `roster.getOrNull(id)` back to `Entity` (net 2→1 probe, a sub-µs
>   micro-opt) **or** switch the payload to `long` ids, which pushes a `getOrNull` probe
>   onto every gather caller's `.faction` read — a decision-cadence **regression**. Slice 1
>   (occupancy) was clean *only* because it is cell-keyed with **no unit payload** — that
>   was the last freebie.
> - **Vision sweep (`FogOfWarService.sweepUnitVisibility`) — not column-walkable.** Its
>   output (`unitVisibility[]`/`fadeAlpha[]`) is keyed by **dense roster index**, which a
>   POSITION-table walk does not preserve (table-row ≠ dense-slot). Re-keying by entity id
>   is a fog re-architecture, not a column-walk slice. (Its `visionRange` reads are already
>   fine: a small by-id contributor list off the VISION component, not an all-unit scan.)
> - **Phase 2 (presence-dispatch) — not a clean lift.** `role` genuinely encodes *transient
>   state* (`FLEE`, `KIT_RETRIEVER`, `OBJECTIVE_CAMPER`), not capability-presence, so
>   converting `behaviorFor(role)` to presence gates is a behavior-model change — the same
>   "not a mechanical lift" trap Phase 1's entangled timers hit. (The `FallbackBehavior`
>   override *is* already presence-gated on `hasAiState` — the clean part was done.)
> - **Phase 3 (behavior bodies) — parked by Phase 0** (~0.02%/frame, not memory-bound).
>
> **Verdict:** the epic delivered its real value at Slice 1 and is otherwise at a natural
> stop. Pushing further is idiom/handle-shrinkage only (the still-open *identity-collapse*
> would subsume the spatial-index id-native conversion), not a systems-half perf win — and
> sinking effort into it now is exactly the premature-perf-engineering the project's
> ship-then-optimize rule warns against. Reopen only alongside the deliberate
> handle-collapse epic, where the spatial index goes id-native as a byproduct.

## The gap

The storage half is done: every numeric per-unit column lives in the archetype
`EntityWorld`, and the engine exposes a `Query` (mask-matched tables) whose intended
use is *"grab the raw column arrays per matched table and run a tight row loop"*
([`archetype-storage.md`](../archetype-storage.md) Core types). **The game systems
never made that move.**

- Only ~4 game files touch the `Query` path
  (`HeavyWeapons` over mechLoadouts, `DroneCrashSystem`, `AirSystem` over airCraft,
  the corpse sweep) — all tiny or optional populations.
- The mainline N≈200 loop —
  [`UnitUpdateSystem.tick`](../../../src/main/java/com/dillon/starsectormarines/battle/decision/UnitUpdateSystem.java)
  — iterates the dense `Entity[]` (`roster.denseArray()`), parallel-streams
  `[0, liveCount)`, and dispatches `behaviorFor(u.role).update(u, sim)`. Inside, state
  is read **by id**: `sim.world().hasAiState(u.entityId)`,
  `sim.world().fallbackTimer(u.entityId)`, etc. Each by-id read is
  `EntityWorld.getInt/getFloat(id, ct, field)` → `requireLoc(e)` (a
  `Long2LongOpenHashMap` probe) + `tables.get(tableIdx)` + column-index lookup —
  **per field, per unit, per tick.**
- That is exactly the surface `EntityWorld` labels
  *"random-access field get/set (off-hot-path; systems iterate columns instead)."*
  `UnitUpdateSystem`'s own javadoc calls itself *"the single named for-loop a future
  ECS refactor needs to find."* This story is that refactor.

**The cache-locality win the whole migration was justified on
([`overview.md`](../overview.md): "perf-gated on hot loops like `UnitUpdateSystem`";
[`world-facade.md`](../complete/world-facade.md): cache-locality "is the whole point") is
currently uncollected, and the hot path may even be *slower* than the old
direct-field `Unit` POJO it replaced.**

## Why this is not a one-commit "grab the arrays and loop"

`behaviorFor(u.role).update(u, sim)` dispatches to a *heterogeneous* set of rich
behaviors — GOAP combatant logic, infantry postures, mech/drone/turret behaviors,
`FleeBehavior`. Each body reads many components, mutates the world, pathfinds, and
sometimes spawns/kills entities. You cannot replace that with a single column kernel.
The conversion is **staged, measurement-gated, and bottom-up** — start with the tight
arithmetic sub-passes that actually benefit from SoA, leave the branchy decision logic
on the handle until a number says otherwise.

## Staged plan (measurement-gated)

### Phase 0 — measure first (do this before any conversion) — DONE (2026-06-29)

**Result recorded in [`phase0-measurement.md`](../phase0-measurement.md); harness is
`EcsAccessBenchTest`.** Headline: at N=200 the by-id path is ~20× the access cost of a
column-walk (~7.6 µs vs ~0.38 µs/tick) — so the gate's literal "within noise → stop"
trigger did **not** fire (column-walk really is faster) — **but** the absolute saving
is only ~7.3 µs/tick ≈ **0.022% of a 30 Hz frame**, and churn barely changes it. So
the win is real-but-architectural, not a frame-time necessity. Recommendation (pending
user direction): do Phase 1 as ECS-idiom completion + `Query`/`CommandBuffer`'s first
combatant-loop consumer, report the tiny number honestly, and **park Phase 3** (deep
behavior-body conversion) — it is not justified on perf. The rest of this section is
the original Phase-0 plan, now satisfied.

Build the A/B harness so every later phase is judged by a number, not faith.

- Add a `TickProfile` phase (or a dedicated micro-bench) that times the
  `UnitUpdateSystem` hot loop at a representative N≈200 battle.
- Capture the **baseline** (today's by-id access). If feasible, also capture the
  pre-migration direct-field-`Unit` cost from git history for a true before/after — at
  minimum, instrument the per-field by-id probe cost vs a column-walk of the same data.
- Decision gate: **if the by-id path is already within noise of a column walk at
  N=200, stop here and re-scope** — the storage migration stands on its own merits
  (composition, corpse-cell-for-free, optional-capability presence) and the systems
  conversion becomes optional polish, not a perf necessity. The audit's strongest
  finding is that nobody knows which world we're in. Find out first.
  ([`jfr_analysis_workflow`](../../../) is the profiling precedent.)

### Phase 1 — convert the per-tick column scans — IN PROGRESS (re-scoped 2026-06-29)

> **Finding that re-scoped this phase (2026-06-29):** the doc's original premise —
> that the timers below are "universal arithmetic sub-passes" liftable mechanically
> into uniform column kernels — **does not hold against the code.** None of
> `cooldownTimer` / `repositionCooldown` / `fallbackTimer` / `wanderDwellTimer` /
> `moveProgress` is a uniform per-tick countdown: each is advanced inside *deliberate*
> per-role control flow. `cooldownTimer` alone is decremented at 5+ gated sites and
> reset at ~12 more; the infantry cooldown decrement is **skipped during the rocket-aim
> window** (`GoapInfantryBehavior.prepareForAction` short-circuits on
> `tickAimAndShortCircuit` *before* `tickCooldowns`) and for solo / falling-back units;
> `fallbackTimer`/`wanderDwellTimer` only tick *while in that state* by design;
> `moveProgress` is movement state reset to 0 at ~20 freeze sites, not a countdown.
> Centralizing any of them into one unconditional sweep would be a **behavior change**,
> not a mechanical lift — and Phase 0 says there is no perf reason to take that risk.
>
> **So Phase 1 is re-scoped to what IS cleanly column-walkable and behavior-neutral:
> the per-tick *scans* that read only column data + entity id (not the `Entity`
> object) and are order-insensitive.** The occupancy rebuild is the first such slice;
> the destination-index and vision-position scans are the same shape if continued.

- **Slice 1 — `NavigationService.rebuildOccupancyMap` ✓ SHIPPED.** The per-tick
  occupancy grid rebuild stopped iterating the roster with by-id `cellX`/`cellY`/
  `hasMovement`/`path` probes and now column-walks the new `BattleComponents.gridOccupants`
  query (`{POSITION}` minus `{CORPSE}` = the dense roster's live set; air carries no
  POSITION → skipped for free). Per matched table it grabs the raw `cellX`/`cellY`
  arrays once; the archetype partitions movers from statics **for free** — a per-*table*
  `has(MOVEMENT)` (not a per-row probe) gates the path-destination reservation. Behavior-
  neutral (occupancy is a saturating sum → order-independent), full suite green. This is
  the **first per-tick combatant-population `Query` consumer** — the idiom the engine was
  built for, now with a real adopter. Justified on idiom/clarity, **not** frame-time (the
  ~1.5 µs/tick it removes is Phase-0-negligible).

The original (now-falsified) framing, kept for the record: *"AI-cadence countdowns,
`moveProgress`, `cooldownTimer`/burst timers — a `Query` for the required component set,
then per matched table grab the raw column arrays and run a row loop; leave the role-
branching decision logic on the `Entity[]` walk, only the arithmetic moves."* The
"grab arrays per matched table, tight row loop" **pattern** is exactly what Slice 1
uses — it's the *targets* (entangled timers) that were wrong, not the technique.

> **Second finding — Phase 1's clean surface is nearly exhausted; the rest is gated
> on the identity epic.** The other per-tick all-unit scans are NOT clean column-walk
> slices, because they need the **`Entity` object**, not just column-data + id:
> `UnitSpatialIndex` / `UnitDestinationSpatialIndex` store `Entity` refs in their
> buckets and `gather` hands `Entity` back to many callers; `FogOfWarService`'s sweep
> reads per-`Entity` fields (`visionRange`). A column-walk gives `entityAt(row)` (a
> `long` id), so converting any of them means changing the payload to ids — which
> ripples across all `gather` callers and is really the **behavior-tier `Entity`-field
> migration** (the sibling epic: move `role`/`visionRange`/… onto world components).
> **So: Slice 1 (occupancy) is likely the only fully-clean per-tick scan slice
> available before that epic lands.** Sequence the identity-field migration next if
> the systems half is to continue; the spatial-index/vision conversions fall out of it
> for free. Until then, Phase 1 is effectively complete at Slice 1.

### Phase 2 — dispatch by presence, not by `role` enum

Where `UnitUpdateSystem` branches on `u.role`, prefer component-presence gates
(`hasMovement`/`hasAiState`/`hasSecondaryWeapon` already exist; add others as needed)
so "what a unit can do" is its component set, not an enum — the Artemis-aspect shape.
This shrinks `role`'s job and is a prerequisite for eventually moving `role` itself off
`Entity` (sibling epic: the behavior-tier `Entity` field migration).

### Phase 3+ — the deep behavior bodies (only if Phase 0 justifies)

The long tail: pushing the rich per-role behaviors to read columns instead of by-id.
High effort, high churn, and only worth it if the measurement says the hot path is
genuinely memory-bound. Do not pre-commit; let Phase 0/1 numbers drive scope.

## CommandBuffer — DECISION (closes backlog item #5)

**Decision (2026-06-28): KEEP `CommandBuffer`. It is committed engine infra, and
THIS epic is its consumer.**

Rationale: the engine core is governed by the **build-it-right-up-front** carve-out
([[feedback_storage_foundation_build_right]]), *not* by ship-then-optimize — the user
explicitly created that carve-out so the storage/engine end-state could be built ahead
of game adoption. `CommandBuffer` is correct, tested (6 engine tests), and is the
sanctioned primitive for the one situation that *requires* deferral: **destroying /
transmuting an entity while walking a `Query` over the table it lives in** (the
swap-pop-during-iteration trap). That situation arrives with Phase 1+ of this epic.

Why it currently has zero callers (and that's fine): today's structural-change sites —
`DeathDispatcher` drain, `AirSystem.reapGoneCraft`, `DroneCrashSystem`'s settled list —
all run in **serial phases at tick barriers, not mid-`Query`-walk**, so they are
already safe with hand-rolled gather-then-apply and don't need deferral. The two models
are not redundant; they cover different situations:

- **Serial, at a barrier** → direct `entityWorld.destroy/transmute` (or a bespoke
  gather) is fine. Keep these.
- **Mid-`Query`-walk** (this epic) → `world.cmd().destroy/add/remove` + the existing
  `BattleSimulation` `flush()` barrier. Adopt here.

Action taken now: the `entityWorld.flush()` call in `BattleSimulation` stays (the
barrier is correctly placed); its comment is updated to point at this epic as the first
real consumer instead of reading as speculative dead code. Do **not** delete
`CommandBuffer`.

## Acceptance criteria

- ~~A measurement exists comparing column-walk vs by-id access for the hot loop at
  N=200, recorded in this directory (no more faith-based perf claims).~~ **MET
  (2026-06-29)** — [`phase0-measurement.md`](../phase0-measurement.md): ~20× access
  ratio, but ~7.3 µs/tick absolute ≈ 0.02% of a 30 Hz frame.
- At least the Phase-1 arithmetic sub-passes iterate via `Query` + raw column arrays,
  with a before/after number.
- Any structural change a column-walking system performs goes through `world.cmd()` +
  `flush()`, never a direct mid-walk `destroy`.
- Determinism: any order-sensitive converted system sorts its working set by
  `entityId` (the `[[dense_registry_swap_pop_trap]]` discipline) — or this epic adds
  the first determinism test and documents the guarantee
  (today the contract in `archetype-storage.md` has zero adopters).

## Risks / open questions

- **The conversion may not pay off.** Phase 0 exists precisely to avoid sinking an epic
  into a hot path that isn't memory-bound at N=200. Respect the gate.
- **Determinism is currently a dead-letter contract** — the engine sorts matched
  *tables* by mask but leaves rows in swap-pop order, and no production system sorts its
  working set by `entityId`. Live ordering rides the roster's insertion+swap-pop order.
  Either adopt the sort where it matters or formally document "order = roster insertion,
  not guaranteed."
- **Query cache invalidation is a single global `tableVersion`** — any new archetype
  created mid-battle (air/drone/corpse transmutes do this) invalidates every held
  `Query`. Cheap at ~8–12 archetypes but worth watching once real systems hold queries.

## Cross-refs

- [`next-session.md`](../next-session.md) § Status — the audit verdict that seeded this.
- [`overview.md`](../overview.md) — SoA design rules (rule 6: capture arrays at top of
  the hot method).
- [`archetype-storage.md`](../archetype-storage.md) — the `Query`/column engine this
  consumes; the determinism + CommandBuffer contracts.
- Sibling epics from the same audit (separate stories when picked up): migrate the
  behavior-tier `Entity` fields onto world components (finishes "entity = id"); fold
  convoy `Vehicle`/`MapVehicle` into the world; live authored-appearance
  (FacingSystem / ANIMATION / FX child entities).
- Memory: [[battle_services_systems]] (Systems = stateless consumers; sim = just the
  tick loop — the north star this epic serves), [[feedback_storage_foundation_build_right]]
  (the CommandBuffer-keep rationale), [[feedback_ship_then_optimize]] (governs the
  game-side conversion: don't over-build past what the Phase-0 number justifies).
