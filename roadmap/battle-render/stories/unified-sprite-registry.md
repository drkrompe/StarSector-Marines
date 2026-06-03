# Unified sprite registry (battle-render — design stage)

> Follow-on to Story A (assets extracted into `BattleSprites`). Story A moved
> the ~900 lines of sprite lifecycle *out of the screen*; it did not unify their
> *shape*. This story collapses `BattleSprites`' ~15 ad-hoc per-category caches
> behind one register/resolve service. **Render-tier only — no sim change.**

## The smell

`ops/battleview/BattleSprites.java` is ~760 lines that are mostly the same triad
repeated once per sprite category:

```
private final EnumMap<K, Cache> xxxSprites = new EnumMap<>(K.class);
private boolean xxxLoadAttempted;
public void ensureXxx() { if (xxxLoadAttempted) return; xxxLoadAttempted = true; … }
public EnumMap<K, Cache> xxxSprites() { return xxxSprites; }
```

repeated for: unit sheets, **dead** unit sheets, vehicle sheets, turret bodies,
turret recoil, drone, drone hub, shuttles, convoy, marine-secondary aim sheets,
decals, tiles, road, floors, water, urban-3, nature, objective icons, engine FX,
plus the path-keyed projectile store. ~15+ instances of the identical
load-once / lazy / EnumMap / accessor pattern, differing only in key type and
asset path.

Two near-duplicate cache record types also exist:
- `UnitSpriteCache` = `SpriteAPI sheet` + `SpriteSheetFrames frames` (sliced).
- `ShuttleSpriteCache` = `SpriteAPI sprite` + `float aspect` (whole-texture).

## The seed pattern that already exists

`BattleSprites.projectileSpriteByPath` is already the target shape in miniature:
a generic `Map<String, ShuttleSpriteCache>` keyed by **texture path**, resolved
through `projectileSprite(String path)`. It's carrier-agnostic
([[feedback_compose_effects_not_carrier]]) — any weapon declaring the same path
shares the one loaded sprite, and the old per-source EnumMaps that mirrored it
were deleted once shots went path-keyed (F3/F5). Generalize *that* and most of
the file collapses.

## Target

A render-tier `SpriteRegistry` (working name) that owns the lazy-load +
slice-once lifecycle for every sheet, keyed by **asset path** (the natural
universal key — every category already loads by a path string), and hands back a
single asset descriptor:

```
SpriteAsset = SpriteAPI sheet
            + SpriteSheetFrames frames   // null/1-frame for whole-texture sprites
            + float aspect               // captured pre-setSize, as ShuttleSpriteCache does
```

unifying `UnitSpriteCache` + `ShuttleSpriteCache` into one type (a whole-texture
sprite is just a 1-frame sheet with a meaningful aspect).

```java
// register/resolve, lazy + idempotent (folds the ensureX + loadAttempted flag)
SpriteAsset a = registry.require(path);   // loads+slices on first call, caches, null on failure
```

The per-category EnumMaps become thin *views*, not stores: callers that want
"the sheet for `UnitType t`" ask `registry.require(t.spritePath)`. The mapping
`domain key → asset path` already lives on the enums (`UnitType.spritePath`,
`ShuttleType.spritePath`, `TurretKind.spritePath`, …). The registry holds **one**
`Map<String, SpriteAsset>`; the EnumMap-per-category bookkeeping evaporates.

### Animation definitions live here too

The registry is also the home for **animation definitions** — a flyweight
`AnimDef` keyed by an `animId`: the ordered list of frame indices (into a sheet) +
per-frame durations + loop/speed. This is the shared, per-*animation* data; the
per-*entity* playback cursor (`animId`, current `frame`, `frameTimer`) lives in the
ECS `Animation` component, and an `AnimationSystem` reads the def by `animId` to
advance the cursor and write the entity's `Sprite.index`. See the appearance/FX
design in
[`ecs-migration/archetype-storage.md`](../../ecs-migration/archetype-storage.md)
§ "Appearance is authored component data". So `animId` is the same kind of
tier-neutral handle as a sheet path — resolved render-side, never held by the sim.

### What "the Id" is — and the sim/render boundary

The conceptual "Id related to an item from the sheet" (from the design chat) is
**the asset path + a frame index**, resolved render-side. It is *not* a handle
stored on the entity. The hard boundary holds (`RenderAppearance` class doc:
*entities never store render fields; `Entity`/`UnitType` never gain `SpriteAPI`*):

- **Sim side** holds conceptual identity only — `UnitType`, `deathPoseIdx`,
  `TurretKind`. Tier-neutral enums/ints.
- **Render side** (`RenderAppearance` + this registry) maps identity → asset.
  `RenderAppearance.of(type)` already carries the type-flyweight tags; the
  registry resolves the concrete sheet.

This is *why* a corpse stays a proper entity rather than a dangling sprite
reference (see [`ecs-migration/component-model.md`](../../ecs-migration/component-model.md)
§ B2.6): `DeadBodyComponent` stores `type`/`deathPoseIdx`, the registry resolves
the sprite. The entity owns the identity; the sprite is *derived*, never held.

## Slicing: keep auto-slice as the default

Current loading auto-slices via `SpriteSheetSlicer` (alpha-gutter bbox
detection), which already handles variable-width cells. **Do not** introduce a
JSON rows/cols descriptor speculatively ([[feedback_no_stopgap_dev]],
[[feedback_ship_then_optimize]]) — auto-slice stays the default. Add a per-sheet
descriptor only if a real sheet appears that the slicer can't segment, and even
then as an optional override keyed by path, not a required config.

## Explicit non-goals

- **No sim change.** This does not touch `UnitRegistry`, components, or the
  death lifecycle. The corpse-as-archetype model is already realized; this just
  cleans the resolver it points at.
- **Live frame selection stays dynamic.** Live infantry frames are
  `f(facing, weaponUp, aim)` recomputed per frame in `emitLiveSprite` — the
  registry hands back the *sheet*, frame selection logic stays in the sweep. A
  "stored sprite handle" cannot represent a live unit and is not the goal.
- **No JSON parse layer** (see above).
- **No new GL / batching change** — the `QuadBatch`/drain path is untouched;
  this is purely the asset-resolution layer feeding it.

## Migration (incremental, each shippable)

1. **Introduce `SpriteRegistry` + `SpriteAsset`** alongside `BattleSprites`;
   back the existing path-keyed projectile store with it first (it's already the
   right shape — lowest-risk proof).
2. **Fold one category at a time** — replace each `ensureX()`/EnumMap with a
   `registry.require(path)` view, leaving the public accessor signature stable so
   callers don't churn. Unit sheets + dead sheets are the natural first real
   category (touches the corpse path we just discussed).
3. **Unify the two cache types** into `SpriteAsset` once both kinds route through
   the registry; delete `UnitSpriteCache`/`ShuttleSpriteCache`.
4. **`BattleSprites` shrinks** to the asset-path constants + any genuinely
   special loaders (the tile sheets capture content px-dims for UV math — those
   may stay as a typed wrapper over a `SpriteAsset`).

Good candidate for Sonnet-delegated mechanical sweeps once the shape is set
([[feedback_delegate_mechanical_sonnet]]) — the per-category folds are
repetitive and verifiable.

## Coordination hazard

A parallel session is reworking components and **moving `SpriteSheetFrames` into
a `battle/sprites/` package** (seen in worktrees). `SpriteAsset` references
`SpriteSheetFrames`, so this work collides head-on with the asset layer. Sync on
the `SpriteSheetFrames` location and land that move first, before cutting the
registry in.

## Open questions

- **Key type.** Asset path `String` (universal, matches the existing projectile
  store) vs. an interned `int` handle (denser, but adds an indirection table).
  Lean path-string until a profile says otherwise.
- **Registry home / package.** `ops.battleview` next to `BattleSprites`, vs. the
  emerging `battle.sprites` package the other session is creating. Resolve with
  the coordination item above.
- **Tile sheets.** They carry content px-dims (`tileSheetPxW/H`) for UV math —
  keep as a typed extension of `SpriteAsset`, or a sibling? Probably a small
  `TileSheetAsset` wrapper; revisit when folding that category.

## Cross-refs

- `ops/battleview/BattleSprites.java` — the file being consolidated.
- `ops/battleview/RenderAppearance.java` — the type-flyweight this resolves
  against (boundary doc lives in its class comment).
- [`ecs-migration/component-model.md`](../../ecs-migration/component-model.md)
  § B2.6 — why the corpse is an entity, not a sprite ref.
- Memory: [[feedback_compose_effects_not_carrier]] (path-keyed seed),
  [[sprite_lazy_load]] (loadTexture-before-getTextureWidth gotcha),
  [[pixel_art_uniform_cell]] (slicing / dst-cell gotcha).
</content>
</invoke>
