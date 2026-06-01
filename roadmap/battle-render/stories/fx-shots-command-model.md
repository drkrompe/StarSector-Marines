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

- **`ShotAppearance` flyweight + capability tags** — the render-side, type-shared
  descriptor for a shot's weapon source. Tags (draft): `hasProjectileSprite`,
  `hasTracer`, `hasContrailRibbon`, `hasEngineTrail`, `hasSmokeTrail`,
  `boostRamp`, plus the per-kind geometry resolved at sweep time
  (`projectileVisualCells`, `arcHeight`, `tracerColor`, sprite-cache selector).
  `ShotEvent` stays dumb sim data — no render fields, mirroring the
  `RenderAppearance`/`Unit` boundary.
- **`ShotRenderService`** (sibling to `UnitRenderService`, `layer() == SHOTS`) —
  stateless per-stratum sweeps in today's submission order: **contrails →
  tracers → projectile sprites**. Tag dispatch replaces the cascade.
- **Contrail state/render split** — the live/decaying trail lifecycle + per-frame
  aging is *state simulation*, not rendering (tell: `renderContrails` is emitted
  *unconditionally even with no shots* just so its callback can age trails). Move
  it to a `ContrailFxService` that `tick()`s (sibling to `impactFx`); the sweep
  emits only the current ribbons. This is the `battle_services_systems` rule
  (services own state, systems are stateless) applied to the render tier.

## The central open question — the flyweight key

`RenderAppearance` keys on a single `UnitType` → one `EnumMap`. A shot's
appearance is keyed by **one of four** weapon-source enums
(`TurretKind`/`MarineSecondary`/`MarineWeapon`/`MechWeapon`), whichever field is
populated on the `ShotEvent`. Options to resolve before F2:

1. **Per-enum tables + a `ShotAppearance.of(ShotEvent)` dispatcher** that reads
   the populated source and delegates to that enum's table. Keeps each table a
   clean `EnumMap`; the dispatch is one `switch` on which field is non-null,
   resolved once per shot per frame (not per stratum).
2. **A shared `WeaponSource` interface** the four enums implement, exposing the
   tag-relevant fields; `ShotAppearance` derives from the interface. Fewer
   tables, but touches sim enums (weigh against the "don't couple sim to render"
   grain — exposing *data* the sim already has is fine; exposing render concepts
   is not).
3. **Resolve tags eagerly onto the `ShotEvent` at spawn** (sim-side). Rejected on
   first pass — that *is* a render field on a sim object.

Lean (1): it's the least sim-invasive and keeps the flyweight relationship pure.
Confirm the exact enum/field names at implementation (`projectileSpritePath`,
`projectileVisualCells`, `arcHeight`, `hasBoostRamp()`, `engineTrail`,
`smokeTrail`, `tracerColor`, `kindUsesContrailRibbon` → `LOCUST`).

## Slices (each independently shippable + in-game verified)

- **F1 — `LINE` draw command (engine add).** Add `LINE` to `DrawCommand.Kind` +
  `DrawList.addLine(...)` + a drain path (a `LineBatch` in `render2d` drawing
  `GL_LINES` via vertex arrays — *not* immediate mode; reuse the lesson from the
  `QuadBatch.flush` work). Migrate `drawTracers` off the raw-`GL_LINES` `Custom`
  onto `LINE` commands. Foundational beyond SHOTS — also unlocks the convoy-debug
  path lines and the zone-overlay grid. Verify: tracers draw identically.
- **F2 — `ShotAppearance` table + tags.** Stand up the flyweight + the chosen
  key strategy; pin the derivation with a `ShotAppearanceTest` (mirrors
  `RenderAppearanceTest`). No pass change yet (mirrors J2).
- **F3 — `ShotRenderService` sweeps.** Stand up the per-stratum sweep consumer;
  migrate projectile-sprite emission + the tracer sweep onto tags, dropping the
  `collectShots` cascade and the tracer `Custom`. Engine/smoke-trail spawns
  (`impactFx.spawnEngineTrail/spawnSmokeTrail`) move into the sprite sweep.
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
