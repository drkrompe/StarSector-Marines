# Live authored-appearance — sprites/rendering as ECS component data

> **STATUS: Phase 1 SHIPPED (`9f1c33f0`, 2026-07-01); Phase 2 next.** The active ECS-migration epic after
> convoy-`Vehicle`-into-world (DONE). The **storage** half of the migration is fully closed
> (every unit / craft / vehicle is a world entity); this is the **presentation-data** half:
> make a live unit's on-screen appearance (facing, frame, aim-pose, animation) **authored
> component data written by presentation systems**, so the renderer becomes a pure `Query`
> collector — the pattern the **corpse `SPRITE`** component and air **`APPEARANCE`** already
> prove. Read [`../overview.md`](../overview.md) for the arc and
> [`../archetype-storage.md`](../archetype-storage.md) for the engine.
>
> Memory: [[feedback_appearance_authored_component]] (the north star — appearance is
> authoritative, controllable component data; render is a pure collector; tier-neutral handle,
> never a `SpriteAPI`), [[feedback_compose_effects_not_carrier]] (`RenderAppearance` / `ShotFx`
> key on capability, not carrier), [[feedback_build_composition_now]] (build the system +
> component, don't defer).

## The gap

Dead units carry an **authored** appearance; live units do **not**.

- **Dead** — `DeadBodySystem.onDeath` writes the frozen death-pose frame into the `SPRITE`
  component (`world.setInt(id, SPRITE, SPRITE_INDEX, deathPoseIdx)`), and `UnitRenderService.
  sweepDeadSprites` **reads it off a `Query` column walk** and draws it. Render is a pure
  collector; the pose is data.
- **Live** — `UnitRenderService` **derives everything per-frame inside the renderer**:
  - `computeFacing` / `computeEightWayFacing` recompute the sprite facing every frame from
    `COMBAT.targetId` (aim at target) → `MOVEMENT` path direction → default `SOUTH`.
  - `pickFrame` / `pickFrameEightWay` map facing → frame index; `weaponUp` (the "just fired"
    pose) is re-derived each frame from `COMBAT.cooldownTimer` vs a 0.25 s window; the
    secondary-aim **sheet override** is picked from `SECONDARY_WEAPON`.
  - The live sweep iterates the **legacy dense roster** (`sim.liveUnitAt(i)`), not a `Query`.

So a unit's on-screen state is a **render-time computation**, not authored data — the exact
inversion the migration's appearance goal names. No sim code stores a unit heading; facing is
**purely presentational** for infantry/mech (their weapon arc/LoS is range+target, not a cone),
so an authored facing is a write-only presentation component the sim never reads (unlike the
real `facingDegrees` steer-state on air/vehicle/turret bodies — a separate family).

## Why this is worth doing

- It's the **presentation-data half** of the migration's own stated goal ("appearance is
  authored component data, render is a pure collector") — the storage half is done, this is the
  matching capability. [[feedback_appearance_authored_component]] records the user asking for it.
- It **unifies live + dead appearance** on one `SPRITE` component and (eventually) one drawable
  `Query` sweep — today they're two divergent code paths (derive vs read).
- It **unblocks real animation** (walk cycles): today a walk-cycle frame index has literally no
  home; an authored `ANIMATION` component is where it lives.
- It follows a **proven precedent twice over**: the corpse `SPRITE` write→read and air
  `APPEARANCE` (authored scalars written by `AirSystem`, read via the stateless `AirAppearance`
  derivation helper).

Honest scope note: this is **uniformity + capability** value, not a frame-time win (facing is
cheap either way at N≈200). It belongs in the build-the-composition carve-out
([[feedback_build_composition_now]] / [[feedback_storage_foundation_build_right]]), not the
don't-over-build caution.

## Component design — extend `SPRITE`, don't add a twin

`SPRITE` (id 3, `{SHEET:int, INDEX:int, FLIP_V:int}`) is already "the authoritative draw-this
quad" (its own javadoc). **Extend it to live units** rather than mint a parallel `FACING`
component — a second component meaning "draw this frame" is a provisional shape the end-state
would just have to merge ([[feedback_storage_foundation_build_right]]). Live + dead then share
one authored component (and, later, one sweep).

| Field | Corpse today | Live (this epic) |
|---|---|---|
| `SPRITE_INDEX` (1) | frozen death-pose frame | per-tick facing/pose frame (authored by `FacingSystem`) |
| `SPRITE_FLIP_V` (2) | 0 (unused) | the weapon-up `SOUTH` vertical mirror (currently derived ad-hoc, never persisted) |
| `SPRITE_SHEET` (0) | 0 = base type sheet | **sheet selector**: 0 = base type sheet, 1 = secondary-aim sheet (renderer maps selector → `SpriteAPI`, keeping the tier-neutral-handle rule) |

- **Membership = "draws as a sheet frame"** (`RenderAppearance.spriteKind == SHEET`) — a
  capability presence ([[feedback_components_by_capability_not_store]]); `WHOLE_SPRITE` / `NONE`
  types carry no `SPRITE`. Corpses already have it.
- The **frame-layout** (4-way / 8-way) and per-type tags (`hasWalkCycle`, frame counts) stay on
  the `RenderAppearance` type-flyweight — the `FacingSystem` reads them to compute the index, so
  the renderer needs neither.
- **`ANIMATION` (id 27, new — later slice)**: `{phase:float, frameIndex:int}` walk-cycle state
  the `FacingSystem` advances and folds into `SPRITE_INDEX`. Deferred (needs walk-cycle art).

## Access model — a `FacingSystem` (presentation system, sim-tick cadence)

A new **`FacingSystem`** (proposed package `battle.appearance`, with a package-info charter
[[feedback_package_info_charters]]) runs in the **sim tick, after combat/movement resolve**
(so it authors the post-tick facing the renderer would otherwise derive), and writes `SPRITE`
for every live SHEET unit. It is a **presentation system in the [[feedback_appearance_authored_component]]
sense**: it runs in the sim world but authors presentation data the sim never reads.
`UnitRenderService` stops deriving and **reads `SPRITE`** — the corpse pattern, extended.

**Cadence is behavior-preserving:** the renderer's `computeFacing` reads **sim cells**
(`world.cellX`), not interpolated render positions, and sim state only changes at tick
boundaries — so authoring once per tick yields the identical discrete 4/8-way facing the
per-frame derivation produced (imperceptible ≤1-tick lag on a discrete value). A stateless
`LiveAppearance` derivation helper (the `AirAppearance` sibling) holds the pure
facing→frame / weaponUp math so it's unit-testable off the system.

## Phases (each leaves build + tests green)

1. ~~**Author `SPRITE` for live units (dormant).**~~ **SHIPPED `9f1c33f0` (2026-07-01;
   Sonnet-implemented from a prescriptive spec, reviewed on the main thread; suite 839 green).**
   Landed as designed with two refinements found in review of `EntityWorld.transmute`:
   (a) `corpseAdd` stays `{SPRITE, CORPSE}` — transmute ORs the add mask, so it's a natural
   no-op for sheet units that already carry SPRITE and still adds it fresh for
   turret/hub/drone deaths (the planned "corpseAdd drops SPRITE" adjustment was unnecessary);
   (b) the REAL corpse ripple is the reverse direction — live-authored `SHEET`/`FLIP_V` ride
   the row-move (a unit can die mid-secondary-aim), so `onDeath` now explicitly zeroes both.
   Also landed: `UnitType.drawnAsSheet()` + `RenderAppearance.derive` deferral,
   `BattleComponents.liveSprites` (`{IDENTITY, POSITION, SPRITE, HEALTH}` — HEALTH excludes
   corpses, no CORPSE exclusion needed), spawn seed `SPRITE_INDEX=3` (south idle, same frame
   in both layouts), `TickProfile.Phase.APPEARANCE`, golden-table + scenario tests.
   Original design (for the record): Add `SPRITE` to the live SHEET-unit spawn
   archetype (presence == sheet-drawn); add `FacingSystem` running **at the tail of the sim
   tick** (after `airSystem`/`groundSystem` deboards, so units spawned this tick are authored
   before render), writing `SPRITE_INDEX`/`FLIP_V`/`SHEET` from the **exact** current derivation
   (extracted verbatim into a stateless `LiveAppearance` helper). **Nothing reads it yet** — the
   renderer still derives (air-Phase-2 pattern: author dormant, flip readers next). Adjust the
   corpse transmute (`corpseAdd` drops `SPRITE` since live already has it; the death write still
   overwrites `SPRITE_INDEX`). Additive; suite green. A test asserts the authored frame equals the
   old renderer derivation for a golden table of (frameLayout, target, path, cooldown, inAim)
   inputs.

   **Membership decision (layering — resolved).** "Sheet-drawn" is classified today in the
   **render tier** (`RenderAppearance.derive`: a switch — `TURRET`/`DRONE_HUB_STRUCTURE` →
   `WHOLE_SPRITE`, `DRONE` → `NONE`, else `SHEET`). The sim-tier `UnitRosterService.allocate`
   **must not** import `ops.battleview.RenderAppearance` (sim→render dependency). Fix: surface the
   classification on the sim **`UnitType`** — a `drawnAsSheet` predicate (or `spriteKind`) — which
   is the natural home (`UnitType` already carries the render-adjacent `frameLayout`, `renderScale`,
   `deadSpritePath`). `RenderAppearance.derive` then **defers to it** (single source of truth,
   de-dups the switch); `allocate` gates `SPRITE` on it. This is a small additive prerequisite
   inside Phase 1.
2. **Flip the renderer to read `SPRITE`.** `emitLiveSprite` reads `SPRITE_INDEX`/`FLIP_V`/`SHEET`
   instead of calling `computeFacing`/`pickFrame`/`weaponUp`; delete those from the renderer
   (moved to `LiveAppearance`). Optionally converge `sweepLiveSprites` + `sweepDeadSprites` onto
   one drawable-`SPRITE` `Query` walk (live sweep leaves the dense roster — a systems-to-columns
   crumb collected for free). Behavior-identical; suite green. Critique.
3. **Walk-cycle `ANIMATION` (id 27) — new behavior, per-type opt-in, ART-DEPENDENT.** Add the
   component + advance it in `FacingSystem`, fold into `SPRITE_INDEX` for types flagged
   `hasWalkCycle`. Behavior-preserving for types without walk frames (gate off). **Blocked on
   walk-cycle sprite sheets** — sequence when art exists; capture the seam now.
4. **Secondary-aim uses the secondary's own target (bug-fix) + FX-as-child-entities (large,
   later).** (a) The aim pose's facing currently comes from the **primary** `COMBAT.targetId`,
   not `SECONDARY_WEAPON`'s aim target — fix in `FacingSystem`. (b) Muzzle-flash / smoke / impact
   as spawned child entities with their own lifecycle components (vs the current `ImpactFx`
   immediate-mode particles) — a separate sub-epic that overlaps the working `ShotFx`/`ImpactFx`;
   scope on its own when reached.

## Phase-2 handoff notes (from the Phase-1 implementation, 2026-07-01)

- **The renderer's frame clamp needs a home.** `emitLiveSprite`'s
  `frameIdx >= frames.frames.length → 0` clamp is sheet-cache-dependent; `FacingSystem`
  deliberately authors the *unclamped* logical frame. Phase 2's reader keeps its own clamp
  (or the sheet-cache lookup defends itself).
- **Aim-sheet-missing fallback.** Today `emitLiveSprite` falls back to the *base* cache when
  the secondary-aim cache is missing/empty; `FacingSystem` authors `SPRITE_SHEET=1` on
  `inAim` regardless (it can't know cache availability — tier-neutral). Phase 2's
  selector→`SpriteAPI` mapping must replicate the fall-back-to-base-on-missing behavior.
- **Phase-4a bug faithfully preserved:** the aim pose's facing still derives from the
  *primary* `COMBAT.targetId`, not the secondary's aim target — ported verbatim per the
  behavior-preserving rule; fix stays scheduled in Phase 4.

## Watch-items

- **Tick ordering is load-bearing.** `FacingSystem` must run **after** the systems that set
  `COMBAT.targetId` + `MOVEMENT` path/idx and decrement `COMBAT.cooldownTimer` this tick, else it
  authors stale facing/pose. Place it at the tail of the sim tick, before render.
- **`SPRITE_FLIP_V` is currently never persisted** — the live renderer computes flipV ad-hoc
  (`weaponUp && facing==SOUTH`). Phase 1 makes it authored; confirm the corpse path (leaves it 0)
  is unaffected.
- **`SPRITE_SHEET` semantics change** from "always 0" to a sheet-selector. Corpses stay 0 (base),
  so no corpse-render change — assert it.
- **Membership must match sheet-drawn** exactly, or a `WHOLE_SPRITE`/`NONE` type gets a stray
  `SPRITE` (harmless but untidy) or a SHEET type misses one (invisible). Post-Phase-1 the single
  source is the sim-`UnitType` `drawnAsSheet` predicate (which `RenderAppearance.derive` defers
  to) — gate both spawn membership and the render sweep on it, never on divergent copies.
- **Corpse transmute ripple** — live units gaining `SPRITE` means `corpseAdd` no longer adds it;
  the death path keeps the row's `SPRITE` and overwrites `SPRITE_INDEX`. Verify a unit that dies
  mid-move still freezes to the death pose (not its last live frame).
- **Facing stays sim-unread** — keep `SPRITE`/facing write-only from presentation; if a future
  cover-facing/LoS-cone feature wants it, that's a deliberate promotion (the `facingDegrees`
  precedent on air/vehicle bodies shows the shared shape), not an accident.

## Acceptance criteria

- A live SHEET unit's drawn frame is **authored `SPRITE` data**, written by `FacingSystem`, read
  by the render collector — `UnitRenderService` no longer derives facing/frame/weaponUp.
- Live + dead appearance share the `SPRITE` component (and ideally one drawable `Query` sweep).
- Behavior is pixel-identical through Phase 2 (a test pins authored frame == old derivation).
- `ANIMATION` (walk cycle) + FX-child-entities have a captured seam (Phases 3–4), not
  necessarily shipped (art / scope gated).
- Facing remains a pure presentation component (sim reads nothing new).

## Cross-references

- [`../overview.md`](../overview.md) — the arc; this is its presentation-data half.
- [`complete/vehicle-into-world.md`](../complete/vehicle-into-world.md),
  [`complete/corpse-archetype-retrofit.md`](../complete/corpse-archetype-retrofit.md) — the
  storage epics + the corpse `SPRITE` precedent this extends.
- Code seams: `ops/battleview/UnitRenderService.java` (`computeFacing` :395, `sweepLiveSprites`
  :260, `sweepDeadSprites` :201, `emitLiveSprite` :304), `battle/unit/DeadBodySystem.java`
  (:79/:85 write), `ops/battleview/RenderAppearance.java` (type-flyweight),
  `battle/air/AirAppearance.java` (stateless derivation-helper model),
  `battle/component/BattleComponents.java` (`SPRITE` :553, next free id 27).
