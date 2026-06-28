# Components by capability, not by store/liveness (design correction)

> **SHIPPED (realized).** This design-correction is now embodied in the archetype
> world: components are decomposed by lifecycle-stable *capability*
> (`IDENTITY`/`POSITION`/`HEALTH`/`COMBAT`/`MOVEMENT`/`AI_STATE`, with `SECONDARY_WEAPON`/
> `MECH_LOADOUT`/`CRASHING`/`KINEMATICS` as optional presence), not by store/liveness —
> death is a row-move that removes the live-only capabilities, exactly the axis below.
> The live form lives in [`archetype-storage.md`](../archetype-storage.md) §component
> decomposition + `BattleComponents`. Kept here as the reference for *why* that axis.

> A correction to how we've been drawing component boundaries, surfaced in design
> review (2026-06-03). Read [`component-model.md`](../component-model.md) first;
> this sharpens its B1 ("group columns into `Position`, `Combat`, …") with the
> *axis* the grouping must follow, and names the smell B1 fixes.

## The flaw

We've been treating **"which store is the entity in"** as a proxy for **"what is
this entity."** That holds only while the capability lines and the store lines
coincide. They stop coinciding the first time a capability crosses the live→dead
boundary.

The user's test that exposes it: *"Would we reasonably give a corpse a cell
location in the future? Yes."* A cell is not a live-combat property — it's a
**spatial** capability that a live unit, a corpse, and a wreck all share. So
gating "has a cell" on "is in the dense live-combat table" is wrong.

## The evidence is already in the tree (twice)

1. **Render position vs. cell — same concept, two homes.** `renderX/renderY` was
   lifted out of the dense `UnitRegistry` into a survivable, id-keyed
   `RenderPositionComponent` (B2.5) *specifically so the corpse keeps it*.
   `cellX/cellY` is the identical spatial-and-wanted-after-death concept, but
   it's still welded into the dense table. The split was decided reactively by
   who-needed-it-first, not by what Position *is*.

2. **`DeadBodyComponent` re-captures `type` + `faction`.** Those aren't
   death-new state — the *live* unit already owned them. We copy them into the
   body at death because the live representation (`UnitRegistry` row / `Unit`) is
   destroyed on release. The only genuinely death-new datum is `deathPoseIdx`.
   So the component is a tiny legitimate **Dead/pose marker** fused with a
   **re-snapshot of persistent identity** — and every future survivor (cell next)
   adds another copied field. That copy-into-body-per-survivor is the leak;
   it's "a solid boundary until it isn't."

## The corrected axis

Decompose by **capability that's stable across the entity's lifecycle**:

- **Identity** (type, faction) and **Position** (cell, render) **persist
  alive→dead.** The entity never loses them.
- **Death = removing the live-only components** (Health / Combat / AiTimers) and
  adding a small `Dead` marker (+ `deathPoseIdx`). *Not* copying survivors into a
  body.
- **Storage is orthogonal.** Dense hot array vs. sparse map is a **perf**
  decision hidden behind an accessor. "Does the corpse have a position" must not
  depend on it.
- **Liveness is explicit** (`hp>0` / a tag / Health-presence), never "am I in the
  table."

A corpse with a cell then falls out for free — it never *lost* its cell, because
cell was a persistent component, not a dense-combat column.

## Reconciling with perf (the real constraint)

`cellX/cellY` is genuinely hot — read every tick by movement, the spatial index,
and facing. Unlike render position (zero dense readers → moved cleanly,
component-model.md B2.5), it can't just become a sparse store. So **Position
stays dual-backed**: dense arrays for live entities, a survivable snapshot for
released ones, behind a `PositionService` accessor that spans both — exactly the
shape `RenderPositionService` already is for render position.

The single `cell → Position` snapshot at the death event is acceptable **because
it populates a first-class Position component the accessor serves uniformly** —
categorically different from bolting another ad-hoc field onto `DeadBody`.

## What this changes

- **`DeadBodyComponent` shrinks** toward a `Dead`/pose marker; `type`/`faction`
  become persistent Identity the entity carries through death (so `MissionResolver`'s
  casualty tally reads a persistent allegiance component, not a death re-capture).
- **A `PositionService`** (cell, dual-backed) joins `RenderPositionService` as the
  spatial accessor; sim hot loops keep reading the dense arrays directly over
  `[0, liveCount())` (unchanged), random-access / dead reads go through the
  accessor.
- **Placement rule going forward:** when adding post-death / cross-lifecycle
  state, ask *"is this a persistent capability the entity keeps?"* If yes →
  id-keyed survivable component + accessor; never copy-into-body at death.

## Guardrails

- This is the **axis** for B1's Position/Combat grouping — realize it when a
  consumer pulls it (the corpse-cell need, the incoming vehicle HP/body work),
  not speculatively ([[feedback_ship_then_optimize]], [[feedback_no_stopgap_dev]]).
- Still **no generic `World`/`Aspect` engine** — Position-as-persistent-component
  first; generalize only when a second/third capability proves the shape
  ([[feedback_build_composition_now]] is about building the concrete component +
  system now, not the speculative engine).
- Obeys the SoA design rules in [`overview.md`](../overview.md) (final accessors,
  tail-swap denseIdx test, parallel arrays).

## Coordination hazard

A parallel session is actively reworking components (and moving `SpriteSheetFrames`
→ `battle/sprites/`). The `cellX/cellY` → `PositionService` decomposition touches
the dense registry + `DeadBodyComponent` directly — **sync before cutting it in**;
this doc is the agreed model, not a race to implement.

## Cross-refs

- [`component-model.md`](../component-model.md) § B1, B2.5, B2.6.
- `battle.unit.components.DeadBodyComponent`, `battle.unit.RenderPositionService`.
- Memory: [[feedback_components_by_capability_not_store]] (the principle),
  [[battle_death_dispatcher_design]], [[dense_registry_swap_pop_trap]],
  [[battle_failloud_accessor_stale_readers]].
</content>
