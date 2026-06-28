# Systems → columns (the systems half of the ECS migration)

> **Status: design stage (seeded 2026-06-28).** This is the epic the storage
> migration was building toward and the audit (2026-06-28) found unstarted. Read
> [`overview.md`](../overview.md) for the SoA design rules and
> [`archetype-storage.md`](../archetype-storage.md) for the engine the game systems
> are meant to consume.

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
[`world-facade.md`](world-facade.md): cache-locality "is the whole point") is
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

### Phase 0 — measure first (do this before any conversion)

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

### Phase 1 — convert the universal arithmetic sub-passes

The parts of the per-unit tick that ARE tight numeric kernels over a universal/near-
universal component set — the best SoA candidates, lowest behavior risk:

- AI-cadence countdowns (`AI_STATE`: `fallbackTimer`, `repositionCooldown`,
  `wanderDwellTimer`) — decrement-toward-zero over the `MOVEMENT`+`AI_STATE` archetype.
- `MOVEMENT` `moveProgress` advance.
- `COMBAT` `cooldownTimer` / burst timers advance.

Pattern: a `Query` for the required component set, then per matched table grab the raw
column arrays once and run `for (int r = 0; r < rowCount; r++)` — the archetype analog
of `overview.md` rule 6. Leave the role-branching decision *logic* on the `Entity[]`
walk for now; only the arithmetic moves. Re-measure after each.

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

- A measurement exists comparing column-walk vs by-id access for the hot loop at
  N=200, recorded in this directory (no more faith-based perf claims).
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
