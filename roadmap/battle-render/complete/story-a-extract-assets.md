# Story A — Extract assets into `BattleSprites` — ✅ SHIPPED

## What landed

`BattleScreen` **4364 → 3441 lines** (~923 out). New files in
`com.dillon.starsectormarines.ops.battleview`:

- `BattleSprites.java` (694) — the asset registry: all sheet `SpriteAPI`s,
  `SpriteSheetFrames`, content px-dims, `EnumMap`/single caches, load-attempted
  guards, the six asset-path constants, and every `ensure*`/`load*` method.
  Log lines retagged `BattleScreen:` → `BattleSprites:`.
- `UnitSpriteCache.java` / `ShuttleSpriteCache.java` — promoted from private
  nested classes to top-level public value types (`public final` fields), so
  `BattleScreen`'s simple-name references kept working via an import.

`BattleScreen` keeps a `private final BattleSprites sprites` and the six
per-sheet `QuadBatch`es, now rebuilt from the loaded sheets via a new
`buildTileBatches()` at the end of the `attach()` ensure-sequence — capacities
preserved (16384/4096/4096/2048/4096/4096). All ~224 consumer reads rewritten
uniformly to `sprites.FIELD()`. Lazy drone ensures route through
`sprites.ensureDroneHubSprite()/ensureDroneSprite()` at the render sites.

Verified: `gradlew build` green + test suite passing. Diff-audited: attach order
preserved, no batch field prefixed, no dangling guards. In-game render check
still recommended on next launch.

Matched the design below exactly (no deviations).

---

# Original design / spec

Move the asset/sprite-cache lifecycle out of `BattleScreen` into a dedicated
`BattleSprites` registry in `com.dillon.starsectormarines.ops.battleview`
(alongside `BattleCamera`). **Zero behavior change.** Pure mechanical move +
accessor sweep. ~600 lines out of the 4364-line god class.

## Design decisions (locked)

1. **`BattleSprites` owns loaded assets only** — the `SpriteAPI` sheets, their
   `SpriteSheetFrames`, the content px-dimensions, the `EnumMap`/single-entry
   caches, the load-attempted guards, and every `ensure*`/`load*` method. It is
   the authoritative asset store.
2. **The per-sheet `QuadBatch`es do NOT move.** They belong to the renderer
   (Story B → `BattleRenderer`). They stay in `BattleScreen` for now. This means
   the six `ensureXSheet()` methods that currently build a batch inline drop
   their batch line; `BattleScreen` rebuilds the batches from the loaded sheets
   via a new `buildTileBatches()` step in `attach()`. Capacities are preserved
   exactly (see below).
3. **`UnitSpriteCache` + `ShuttleSpriteCache` become top-level public classes**
   in `battleview` (own files, `public final` fields). This keeps every
   simple-name reference in `BattleScreen` working with just an import — no
   per-site type rewrite.
4. **Accessor convention:** every moved field is exposed via a zero-arg getter
   named exactly the field name (`tileSheet()`, `tileSheetPxW()`,
   `unitSprites()` returns the `EnumMap`, etc.). Consumer rewrite rule is
   uniform: `FIELD` → `sprites.FIELD()`.

## What moves to `BattleSprites`

**Caches / sheets / frames / px-dims + their load-attempted guards:**
`unitSprites`, `unitDeadSprites`, `unitSpritesLoadAttempted`; `vehicleSheets`,
`vehicleSheetsLoadAttempted`; `turretSprites`, `turretRecoilSprites`,
`turretProjectileSprites`, `turretSpritesLoadAttempted`;
`marineSecondarySprites`, `marineWeaponProjectileSprites`,
`mechWeaponProjectileSprites`, `marineSecondaryAimSheets`,
`marineSecondarySpritesLoadAttempted`; `decalSheet`, `decalFrames`,
`decalSheetLoadAttempted`; `droneHubSprite`, `droneHubSpriteLoadAttempted`,
`droneSprite`, `droneSpriteLoadAttempted`; `tileSheet`, `tileSheetPxW`,
`tileSheetPxH`, `tileSheetLoadAttempted`; `roadSheet`, `roadSheetPxW`,
`roadSheetPxH`, `roadSheetLoadAttempted`; `floorsSheet`, `floorsSheetPxW`,
`floorsSheetPxH`, `floorsSheetLoadAttempted`; `waterSheet`, `waterSheetPxW`,
`waterSheetPxH`, `waterSheetLoadAttempted`; `urbanTile3Sheet`,
`urbanTile3SheetPxW`, `urbanTile3SheetPxH`, `urbanTile3SheetLoadAttempted`,
`urbanTile3Frames`; `natureSheet`, `natureSheetPxW`, `natureSheetPxH`,
`natureSheetLoadAttempted`, `natureFrames`; `shuttleSprites`,
`shuttleSpritesLoadAttempted`; `convoySprites`, `convoySpritesLoadAttempted`;
`engineFlameSprite`, `engineGlowSprite`, `engineFxSpritesLoadAttempted`;
`iconAlarm`, `iconDanger`, `iconStar`, `iconsLoadAttempted`.

**Asset-path constants** (used only by load methods, verified):
`SPRITE_DECAL_SHEET`, `ENGINE_FLAME_SPRITE`, `ENGINE_GLOW_SPRITE`,
`ICON_ALARM`, `ICON_DANGER`, `ICON_STAR`.

**Methods (verbatim, minus batch construction):** `ensureTileSheet`,
`ensureRoadSheet`, `ensureFloorsSheet`, `ensureWaterSheet`, `ensureNatureSheet`,
`ensureUrbanTile3Sheet` (each loses its `QuadBatch` line), `ensureEngineFxSprites`,
`loadEngineFxSpriteOrNull`, `ensureShuttleSprites`, `ensureConvoySprites`,
`ensureObjectiveIcons`, `loadIconOrNull`, `ensureUnitSheets`,
`ensureVehicleSheets`, `ensureMarineSecondarySprites`, `ensureTurretSprites`,
`ensureDroneHubSprite`, `ensureDroneSprite`, `loadTurretSpriteInto`,
`loadUnitSheet`. These become `public` on `BattleSprites`. `BattleSprites` gets
its own `Logger` (keep the `"BattleScreen: ..."` log strings verbatim to avoid
churn / log-grep surprises — or retag to `BattleSprites:`; pick retag for
honesty, it's log text only).

## What STAYS in `BattleScreen`

- The six per-sheet batches: `urbanBatch`, `roadBatch`, `floorsBatch`,
  `waterBatch`, `urbanTile3Batch`, `natureBatch` — **do not** prefix these with
  `sprites.`; they remain `BattleScreen` fields.
- `solidBatch`, `contrailBatch` (not sheet-tied), `decalAccumulator`,
  `lightAccumulator`, `impactFx`, `flybyOverlay`, `compoundMarkers`,
  `contrailsLive`, `contrailsDecaying`, `lastAdvanceDt`.
- Render-timing / render constants: `RECOIL_DURATION`, `RECOIL_DISTANCE_FRAC`,
  `ROAD_FILL`, `COURTYARD_FILL`, `CROSSWALK_*`, `GROUND_*_INSET_PX`, `timeOfDay`.
- All `render*` / `draw*` methods (their asset reads get the `sprites.` prefix).

## Batch rebuild (preserve exactly)

`attach()` calls each `sprites.ensureX()` (same order as today), then
`buildTileBatches()`:

| batch            | sheet (via sprites)   | pxW/pxH (via sprites)              | capacity |
|------------------|-----------------------|------------------------------------|----------|
| `urbanBatch`     | `tileSheet()`         | `tileSheetPxW/H()`                 | 16384    |
| `roadBatch`      | `roadSheet()`         | `roadSheetPxW/H()`                 | 4096     |
| `floorsBatch`    | `floorsSheet()`       | `floorsSheetPxW/H()`              | 4096     |
| `waterBatch`     | `waterSheet()`        | `waterSheetPxW/H()`              | 2048     |
| `urbanTile3Batch`| `urbanTile3Sheet()`   | `urbanTile3SheetPxW/H()`         | 4096     |
| `natureBatch`    | `natureSheet()`       | `natureSheetPxW/H()`            | 4096     |

Each built only if the sheet is non-null and the batch is still null (preserves
today's lazy "batch exists iff sheet loaded" invariant across re-attach).

## Lazy ensures

`ensureDroneHubSprite` / `ensureDroneSprite` are called from render passes
(today at the drone-hub and drone passes), not `attach()`. After the move those
call sites become `sprites.ensureDroneHubSprite()` / `sprites.ensureDroneSprite()`
immediately before reading `sprites.droneHubSprite()` / `sprites.droneSprite()`.

## Verify

`gradlew.bat build` green, then an in-game battle renders identically (units,
vehicles, turrets, shuttles, convoy, drones, decals, tiles incl. road/nature/
urban3/water, engine FX, objective icons all present). Diff review: confirm no
batch field got a `sprites.` prefix and capacities are unchanged.
