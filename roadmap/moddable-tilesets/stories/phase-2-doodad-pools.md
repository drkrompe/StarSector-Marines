# Phase 2 — Doodad pools (gen mapping as data: first slice)

The first slice of Phase 2 (**gen mapping as data**, see [`../overview.md`](../overview.md)).
Moves the hardcoded doodad **pools + per-prop cover** out of `TileManifest` /
`Doodad.defaultCoverFor` into data, behind the documented `GenMappingRegistry`
seam. Carving / placement algorithms stay in Java (the data/algorithm seam).

## Scope decision — props are data, resolvers stay code

The doodad inventory (every `new Doodad(...)` / `addDoodad(...)` site) splits in two:

- **Prop doodads** — furniture on `urban-tileset.png`: crates, boxes, chests,
  chairs, shelves, desks, the closed-door panel, rubble decals, damaged props.
  Genuine *content*: a pool of props, each with an intrinsic tactical cover.
  → **become data-driven `DoodadDef`s with cover**; the 4 named pools become a
  `doodadPools` mapping (DistrictTheme → doodad ids).
- **Resolver / marker doodads** — on `urban-tileset-2.png`: turret embankments
  placed by the directional resolvers `TileManifest.turretEmbankment(relX,relY)`
  / `turretBowOut`, the LZ-corner arrows, the LZ pad, the light-post vent.
  These are *geometry / markers*, not pools — `DefensePostStamper` already
  passes an **explicit** `COVER_HEAVY` (never used `defaultCoverFor`).
  → **stay code resolvers** (same rule that kept `GridLayout` autotiles in code,
  Phase 1c); the few sites that lean on `defaultCoverFor` for a `NONE` marker
  pass an explicit cover instead. This is what lets `defaultCoverFor` retire.

Rationale: the track's invariant is *pools/membership/properties → data;
geometry/carve/resolvers → code*. Embankment/arrow placement is a resolver.

## Cover parity (and the payoff)

Each `DoodadDef`'s cover is set to **exactly** what `Doodad.defaultCoverFor`
returns for its `(col,row)` today — behavior-preserving by construction, pinned
by a parity test. That deliberately preserves today's **cover gaps**: chairs
`(6,1)/(7,1)`, shelves `(5..8,3)`, desks `(9,2)/(9,3)` all currently score
`COVER_NONE`. Those look wrong (a shelf should be heavy cover) — but the point
of this slice is that **fixing them is now a one-line JSON edit**, not a code
change. Logged as a tuning follow-up below, not fixed in the behavior-preserving
slice.

## Model

```
battle/world/tiles/
  DoodadCover.java   enum NONE(0)/LIGHT(1)/MED(2)/HEAVY(3) + level() + fromJson()
  DoodadDef.java     id, sheetPath, col, row, DoodadCover cover
  TileRegistry       + doodadsById, doodad(id)/hasDoodad(id); parses a sheet's
                       "doodads": [{id,col,row,cover}] array (id shares the
                       requireUniqueId namespace)
battle/world/gen/
  GenMappingRegistry.java   the Phase 2 seam. installed() singleton (mirrors
                            TileRegistry). Loads data/tilesets/*.mapping.json.
                            v1: doodadPool(DistrictTheme) -> List<DoodadDef>.
```

Data:
- `mod/data/tilesets/urban-tileset.tileset.json` gains a `"doodads"` array — the
  ~22 prop cells, each `{ id, col, row, cover }`.
- `mod/data/tilesets/urban.mapping.json` (new) — `"doodadPools": { MIXED:[…],
  RESIDENTIAL:[…], WAREHOUSE:[…], SKY_PORT:[…] }`, values = doodad ids.

## Sub-slices

1. **Core + data (additive) ✅ `fda40a33`** — `DoodadCover`, `DoodadDef`, registry
   parse, 23 `doodads` defs, `GenMappingRegistry` + `urban.mapping.json`, parity
   test. No consumer flipped.
2. **Flip consumers ✅ `259a9b8e`** — `Doodad(int,int,DoodadDef)` ctor; pool
   consumers (`UrbanMapGenerator` + ~10 BSP fillers) read
   `GenMappingRegistry.doodadPool(...)`; hardcoded prop sites (`BuildingLayouts`,
   `Park`/`Plaza`/`WastelandRubble`) resolve defs by id; marker sites (LZ
   arrows/pad) pass explicit cover. Fanned out to Sonnet subagents per
   [[feedback_delegate_mechanical_sonnet]].
3. **Retire ✅ `259a9b8e`** — deleted `Doodad.defaultCoverFor` + the two
   cover-deriving `TileFrame` ctors; deleted `TileManifest.DOODAD_POOL` /
   `RESIDENTIAL_/WAREHOUSE_/SKYPORT_DOODADS` + `doodadPoolFor`; tests updated off
   them; `DoodadMappingParityTest` converted to a frozen golden. `DistrictTheme`
   stays (used widely).

> **Refinement discovered mid-flip:** `BuildingCommercialFiller` has a bespoke
> pool (used by tiny-commercial sparse scatter) that isn't a `DistrictTheme`. So
> pools are keyed by a **String pool-id** (the 4 theme names + `COMMERCIAL`), with
> `doodadPool(String)` primary and a `doodadPool(DistrictTheme)` convenience.
> `BuildingShellCore.BuildingConfig` carries a `String doodadPoolId` resolved at
> scatter. This generalizes cleanly for Phase 3 mod-merge (named pools).

## Follow-ups (don't lose)

- **Cover tuning** — revisit the `NONE` gaps (chairs/shelves/desks) once data-driven;
  a shelf reading `HEAVY`, a chair `LIGHT`, etc. Pure JSON edit now.
- **Resolver doodads as data** — if a submod ever needs to reskin embankment /
  LZ-marker art, revisit folding those into defs; deferred as geometry-stays-code.
- Later Phase 2 slices: `GroundKind` → tile render dispatch; per-`BlockKind`
  filler params (groundPool/chances) — extend the same `*.mapping.json` +
  `GenMappingRegistry`.
