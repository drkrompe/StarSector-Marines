# Phase 2 — FX command-model migration (SHOTS as the flyweight template)

> **Status: design-stage / not started.** The structural reorg (Stories A–J +
> Final) and the `QuadBatch.flush` perf spike are done. This opens the *next*
> battle-render epic: dissolving the residual `Custom` escape hatches into the
> command model, using SHOTS/FX as the template — applying the Story-J
> flyweight + capability-tag + stateless-sweep pattern one tier earlier.

## Why this, why now

`renderWorld` is collect-all → drain-all, but ~10 of the `worldSystems` entries
are still `RenderSystem.of(layer, … addCustom(…))` — own-GL blobs the drain
can't see into or batch. The reorg *wrapped* them in the systems list; it did
not express them *as data*. So "the command stream is the auditable unit" is
still aspirational for those passes. SHOTS is the sharpest example: its
projectile sprites are real `SPRITE` commands, but `drawTracers` (raw
immediate-mode `GL_LINES`) and `renderContrails` (RibbonBatch **+** stateful
trail lifecycle) are two `Custom`s — and `collectShots` dispatches them with a
hand-rolled `if turretKind … else if marineSecondary … else if marineWeapon …`
cascade (`BattleRenderer.java:700-846`).

That cascade is exactly the `instanceof`/ladder Story J replaced with
`RenderAppearance.of(UnitType)` + capability tags. We already proved the
pattern; this leans into it instead of inventing a competing one.

## Explicitly rejected: a `Renderable` interface

A `Renderable`/`ShotRenderable`/`TracerRenderable` hierarchy (or a per-event
`renderLike(event)`) puts draw responsibility back on the event object — the
"smart object draws itself" model Story J deliberately walked away from (it
refused to put `SpriteAPI` on `Unit`; the boundary is codified in
`overview.md`'s watch-outs). It would stand up a second rendering paradigm beside
the command stream and **lose batching**: a per-event polymorphic render call
interleaves strata, defeating the per-sheet/per-kind coalescing the drain gives
us (the same reason J5 split into per-stratum sweeps). Composition is the right
instinct; its idiomatic form here is the flyweight + tag-driven sweep, not an
interface on the event.

## Target shape (mirror of UNITS)

- **`ShotFx` flyweight — a carrier-agnostic effect *composition*** (not a
  carrier descriptor). FX is a property of the *shot* (composed from the
  *weapon*), not of who fired it: a marine grenade launcher arcs + contrails
  exactly like a turret mortar; a marine missile will boost exactly like a
  Locust. So `ShotFx` is a record of opt-in effects + params, and **consumers
  key only on the effects, never on the carrier** — adding arc + contrail to a
  future weapon is a one-line declaration, no sweep edits. `ShotEvent` stays dumb
  sim data — no render fields, mirroring the `RenderAppearance`/`Unit` boundary.
- **`ShotRenderService`** (sibling to `UnitRenderService`, `layer() == SHOTS`) —
  stateless per-stratum sweeps in today's submission order: **contrails →
  tracers → projectile sprites**. Tag dispatch replaces the cascade.
- **Contrail state/render split** — the live/decaying trail lifecycle + per-frame
  aging is *state simulation*, not rendering (tell: `renderContrails` is emitted
  *unconditionally even with no shots* just so its callback can age trails). Move
  it to a `ContrailFxService` that `tick()`s (sibling to `impactFx`); the sweep
  emits only the current ribbons. This is the `battle_services_systems` rule
  (services own state, systems are stateless) applied to the render tier.

## The model — RESOLVED (carrier-agnostic `ShotFx` composition)

The earlier draft keyed a `ShotAppearance` by *carrier* (turret/marine/mech) with
a `SpriteSource` discriminator. **Superseded:** FX is the shot's effect
composition, carrier-agnostic. The flyweight stays (right for perf +
single-source-of-truth), but it's an effect record, and **consumer sweeps key on
effects, never on carrier**.

`ShotFx` (render-side flyweight; `ShotEvent` stays dumb):

```
record ShotFx(
  Body          body,        // SPRITE(spritePath, visualCells) | TRACER(Color?)
  float         arcHeight,   // 0 = flat
  boolean       boostRamp,
  boolean       engineTrail,
  boolean       smokeTrail,
  ContrailStyle contrail     // null = none
)
// contrail sweep: shots.filter(s -> of(s).contrail != null)
```

**Derivation stays per-enum, render-side** — `ShotFx.of(ShotEvent)` switches on
the single non-null weapon source (mutually exclusive per `ShotEvent`'s
contract; all-null → a faction-default tracer) and reads that enum's *data*
fields into the uniform record. This keeps sim enums free of render types (same
relationship `RenderAppearance.of(UnitType)` has), while the *consumer* sees only
the composition. Rejected a shared `WeaponSource` interface: the enums' FX field
sets diverge (`arcHeight` 2/4, `engineTrail`/`smokeTrail`/`tracerColor` 1/4 each)
→ fat interface + render accessors pushed onto sim. Only pays off with a sim-side
weapon unification (out of scope).

Two concrete changes this model forces vs. the carrier-keyed draft — both are
what make the grenade-launcher example *free*:

1. **Path-keyed projectile-sprite cache → the discriminator vanishes.** The
   per-carrier sprite maps (`turretProjectileSprites()` etc.) are an artifact;
   the asset is just a `projectileSpritePath`. Re-key the texture cache **by
   path** so `Body.SPRITE(path, cells)` resolves uniformly for *any* weapon. No
   `SpriteSource` switch. (`visualCells` stays a per-weapon field in `ShotFx` —
   only the texture lookup goes path-keyed.) This is a small `BattleSprites`
   change folded into F2/F3.
2. **No `LOCUST` special-cases.** `boostRamp` and `contrail` become general
   per-weapon declarations, not `== LOCUST` checks — a future boosting marine
   missile sets `boostRamp = true` and it just works, no sweep or dispatch edits.

Two facts the model preserves from the enum read:

- **Contrail is a modifier, not a stratum.** `LOCUST` emits a projectile sprite
  *and* a contrail ribbon (and suppresses its smoke puff). A shot can appear in
  both the sprite sweep and the contrail sweep — like UNITS' footprint + body
  over one entity. Sweeps are not mutually exclusive.
- **Tracer color is per-shot for the faction default.** `Body.TRACER(Color?)`
  carries the type-fixed color (marine primaries) or `null` → the sweep resolves
  `MARINE_TRACER`/`DEFENDER_TRACER` from `shooterFaction` (dynamic-at-sweep, like
  `RenderAppearance` leaves hp/facing).

Derivation to pin in F2's test (every enum value of all four sources + the
no-source case): TurretKind/MarineSecondary/MechWeapon → `Body.SPRITE`;
MarineWeapon → `Body.SPRITE` iff `projectileSpritePath != null` (SMG), else
`Body.TRACER(tracerColor)`; no-source → `Body.TRACER(null)`. `arcHeight` from
TurretKind/MechWeapon (else 0); `engineTrail` from MechWeapon; `smokeTrail` from
TurretKind; `boostRamp`/`contrail` from the weapon's own declaration.

## Slices (each independently shippable + in-game verified)

- ~~**F1 — `LINE` draw command (engine add).**~~ ✅ **SHIPPED & VERIFIED.**
  `LINE` kind on `DrawCommand` (+ `setLine`; reuses `cx,cy`/`w,h` as endpoints,
  `angleDeg` as width) + `DrawList.addLine(...)` + a `LineBatch` (`render2d`,
  `GL_LINES` via client-side vertex arrays — not immediate mode; carries the
  array-buffer guard + client-attrib bracketing from the `QuadBatch.flush` work).
  Drain gained a `LineBatch` param + `LINE` case, a peer to `SOLID_RECT`:
  flush-on-kind-switch and flush-on-width-change (width is per-flush GL state).
  `drawTracers` migrated off the raw-`GL_LINES` `Custom` to `addLine` emission in
  `collectShots` (same exclusion logic, same submission slot) and deleted.
  In-game: tracers draw identically; sprite shots unaffected. Foundational beyond
  SHOTS — the `LINE` primitive also unlocks the convoy-debug paths + zone grid.
- **F2 — `ShotFx` composition + path-keyed sprite cache.** Stand up the `ShotFx`
  record + `of(ShotEvent)` derivation; re-key the projectile-sprite texture cache
  in `BattleSprites` by path (so `Body.SPRITE` resolves carrier-agnostically and
  the discriminator never exists). Pin the derivation with a `ShotFxTest` (mirrors
  `RenderAppearanceTest`). No pass change yet (mirrors J2).
- **F3 — `ShotRenderService` sweeps.** Stand up the per-stratum sweep consumer
  keyed purely on `ShotFx` effects; migrate projectile-sprite emission + the
  tracer sweep, dropping the `collectShots` carrier cascade and the tracer
  `Custom`. Engine/smoke-trail spawns (`impactFx.spawnEngineTrail/spawnSmokeTrail`)
  move into the sprite sweep, gated on the `engineTrail`/`smokeTrail` effects.
- **F4 — Contrail state/render split.** Extract `ContrailFxService` (owns
  `contrailsLive`/`contrailsDecaying` + aging, `tick(dt)`); the sweep emits the
  ribbon. Decide: a `RIBBON` `DrawCommand` (ribbon becomes data) vs. keep
  `RibbonBatch` but make the flush drain-owned. Removes the contrail `Custom` and
  the unconditional-emit smell.
- **F5 — Delete inline fallbacks.** Remove `collectShots`/`drawTracers`/
  `renderContrails` from `BattleRenderer`; SHOTS fully command-driven. Move this
  doc to `complete/`.

## Template payoff — the remaining `Custom` passes

Once SHOTS is the worked example, the other `Custom`s sort into two buckets:

- **Migratable geometry** (become commands via the same recipe; several need the
  F1 `LINE` primitive): convoy-debug paths, zone-debug overlay, objective
  progress arcs + rings, compound capture markers, debug highlights. `impactFx`
  is already a service → its render can emit commands.
- **Legitimately `Custom`** (per `overview.md` constraint #2 — FBO blits /
  non-quad blend): `DecalAccumulator` and `LightAccumulator` (FBO + multiply
  blend), and arguably the flyby overlay. These *stay* `Custom`; the goal isn't
  zero `Custom`s, it's that every `Custom` is a genuine FBO/own-GL escape, not a
  geometry pass hiding from the drain.

Cross-ref: `overview.md` (paradigm + Artemis bridge table), `complete/story-j-units.md`
(the exemplar), memory `[[battle_services_systems]]`, `[[render2d_batching]]`,
`[[feedback_entity_for_loop_endgame]]` (default to ECS shape).
