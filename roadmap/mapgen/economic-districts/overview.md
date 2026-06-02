# Economic districts — the city looks like its economy

> Bridge consumer #2 (see [`../../campaign-battle-bridge/overview.md`](../../campaign-battle-bridge/overview.md)
> § "urban composition"), scoped up from "reweight the existing themes" to its
> true size: a **district vocabulary** where a mining world *reads and fights*
> like a mining world, a spaceport like a spaceport. Districts become
> **economy-reflective tactical terrain types**, not cosmetic reskins.

## Why this is bigger than a reweight

The generator's content layer today is **generic, not economic**. The 16
`BlockKind`s (`bsp/BlockKind.java`) are `RESIDENTIAL / COMMERCIAL /
INDUSTRIAL_YARD / PARK / …`; the 9 `MapDistrictTheme`s are pure weight-tables
over them. There is no "refinery," "headframe," or "pad field" — a mining world
rendered today is just `INDUSTRIAL` with the dial turned up. Making the planet's
economy legible on the ground needs new archetypes with identity.

The de-risking find (see the content-layer survey): **rendering is 100% tile +
doodad-scatter — zero bespoke building art.** Every building is a hollow
autotile shell (`BuildingShellCore`) + a doodad pool from shared sheets
(`TileManifest`). So a new district is design + a filler + a doodad-pool pick —
**not an art commission.** (A handful of new doodad *cells* — headframe, tank,
silo silhouettes — would sharpen the read; tracked as an optional polish slice,
never a blocker.)

## The load-bearing constraint: identity lives in the nav layout

**Doodads are pure visual — they never block movement or grant cover.** So a
district's three identities map to three different layers, and the tactical one
is the hard part:

- **Economic identity** — which vanilla industry summons the district (the
  bridge read; see "Dependency" below).
- **Visual identity** — `GroundKind` choice + doodad pool. Cheap, decorative.
- **Tactical identity** — the **walkable / cover / wall / impassable** cells the
  filler stamps. This is what makes a district *fight different*, and it must be
  real nav-grid structure, not doodads. "Ore heaps as broken cover" = actual
  cover-bearing cells; "impassable pit" = non-walkable cells; doodads only dress
  them. This is where the [[feedback_world_reactive_over_expressive]] bar is met
  — a district is a terrain type, not a paint job.

## Dependency — extend the bridge to carry the economy

`TargetProfile` (the [campaign → battle bridge](../../campaign-battle-bridge/overview.md))
distills only `defenseLevel` + `spaceportTier` today. This feature needs the
planet's **economic function mix**. To keep `battle.world.gen` campaign-free,
introduce a stable, vanilla-decoupled enum in `battle.world.gen`:

```
enum EconomicFunction { HABITATION, COMMERCE, HEAVY_INDUSTRY, SPACEPORT,
                        MINING, REFINING, AGRICULTURE, MILITARY }
```

- `TargetProfileResolver` maps vanilla `Industries.*` → `EconomicFunction`
  (e.g. `MINING`/`TECHMINING` → MINING; `REFINING`/`FUELPROD` → REFINING;
  `FARMING`/`AQUACULTURE` → AGRICULTURE; `SPACEPORT`/`MEGAPORT` → SPACEPORT; …)
  and `TargetProfile` carries an `EnumSet<EconomicFunction>` (presence first;
  per-function weight from industry size/upgrade is a later refinement).
- `DistrictMap` (legacy) and `BiomeMap` (conquest) read the set and **weight
  district selection toward present functions** — a mining world's interior roll
  pulls in `MINING_DISTRICT`; a soft farming colony pulls in `AGRICULTURE`. With
  an empty set (`NEUTRAL`, no campaign) selection is unchanged — byte-identical.

This extension is the shared substrate every district below sits on.

## The four lead districts (Tier B specs)

Each: economic trigger · visual (ground + doodads) · **tactical (nav layout)** ·
new `BlockKind`/filler. All reuse existing tile sheets.

### Spaceport — *open killing ground* (partial today → sharpen)
- **Trigger:** `SPACEPORT` / `MEGAPORT` (also raises tier, already in profile).
- **Visual:** large `STRIPED` pad fields, `LANDING_ZONE` markers, a control-tower
  shell (POI), warehouse shells around the apron.
- **Tactical:** wide expanses of open walkable pad with **sparse, isolated hard
  cover** (parked-crate clusters, tower base) — a frontage you cross under fire,
  the natural home of the defense-intensity overwatch line. Long sightlines.
- **Build:** new `SpaceportFiller` (or generalize `HARBOR_PORT` + `LandingZoneFiller`):
  apron-scale `STRIPED` fill + control-tower `BuildingShellCore` POI + scattered
  cover islands. Closest to existing → cheapest "new."

### Mining — *broken cover around impassable pits* (new)
- **Trigger:** `MINING` / `TECHMINING`.
- **Visual:** `DIRT`/`RUBBLE` ground, headframe shell, conveyor lines, ore-heap
  doodads.
- **Tactical:** **impassable open pits** (non-walkable, see-through hazard cells —
  channel movement without blocking sight) ringed by **ore heaps as cover-bearing
  cells** (low hard cover you fight around). Irregular, cover-rich, movement
  funneled by pit edges — the inverse of the spaceport's open frontage.
- **Build:** `BlockKind.MINING_SITE` + `MiningFiller`: carve a pit (new
  impassable treatment — likely a `GroundKind.PIT` or reuse non-walkable +
  see-through), stamp ore-heap cover clusters, a headframe shell, conveyor
  doodad lines. Strongest tactical-identity payoff.

### Refinery / fuel — *hard-cover tank clusters* (new)
- **Trigger:** `REFINING` / `FUELPROD`.
- **Visual:** cylindrical tank footprints (round non-walkable blobs), pipe-run
  doodads, flare-stack shells.
- **Tactical:** clusters of **small round hard-cover** (tanks) with lanes between
  — a cover-dense maze, defender-favorable, with explosive flavor (tanks as
  destructible high-HP cover that, when downed, open lanes — a future tie to the
  wall-HP system). Mid-range firefight terrain.
- **Build:** `BlockKind.REFINERY` + `RefineryFiller`: stamp round non-walkable
  tank footprints (cover at their rim) on an open `STONE`/`DIRT` pad, pipe-run
  doodads between them, a flare-stack POI.

### Agriculture — *wide-open long sightlines* (new)
- **Trigger:** `FARMING` / `AQUACULTURE`.
- **Visual:** field-grid ground (`GRASS`/`DIRT` in rows), silo footprints,
  greenhouse shells.
- **Tactical:** **near-zero cover over long open fields** — the tactical opposite
  of downtown CQB; punishes movement in the open, rewards the few hard points
  (silos = sparse round cover; greenhouses = *see-through but non-walkable* glass
  walls — cover you can be seen through, a unique read). Sniper/overwatch terrain.
- **Build:** `BlockKind.AGRI_FIELD` + `AgriFiller`: open field fill with row
  texture, occasional silo (round non-walkable) + greenhouse shell (see-through
  walls).

## Slice plan

0. ~~**Substrate (shared dependency).** `EconomicFunction` enum +
   `TargetProfile.functions` + resolver mapping + `DistrictMap`/`BiomeMap`
   economy-weighted selection.~~ ✅ **shipped** — see
   [`complete/slice-0-substrate.md`](complete/slice-0-substrate.md). The eight
   `EconomicFunction`s map onto the existing nine themes via `EconomicZoning`;
   `BiomeMap`'s CITY band (the urban bulk) and `DistrictMap`'s interior rolls
   now lean toward the world's economy, `NEUTRAL` byte-identical. The plumbing is
   proven on known output; slices 1–4 are now pure content drops onto a live pipe.
1. ~~**Spaceport** (cheapest new — mostly sharpening the existing port path).~~
   ✅ **shipped** — see [`complete/slice-1-spaceport.md`](complete/slice-1-spaceport.md).
   `SPACEPORT_PAD` + `SpaceportFiller`: open walkable apron, sparse isolated
   hard-cover islands (real cover via the bake), a control-tower COMMS POI;
   wired into the `HARBOR_PORT` theme so every conquest port band gains the open
   frontage. Connectivity gate holds.
2. **Mining** (strongest identity; introduces the impassable-pit nav treatment).
3. **Refinery / fuel** (round hard-cover footprints; reuses much of mining).
4. **Agriculture** (open + see-through greenhouse walls).
5. *(optional polish)* bespoke doodad sprites (headframe, tank, silo) for sharper
   silhouettes — the only slice that touches art.

Each district slice = new `BlockKind` (where applicable) + filler + theme +
register in `BspCityGenerator` + a preview/validation test, mirroring how the
existing fillers are structured. Connectivity + garrison-deployability invariants
(`MapValidationScanTest`) gate every one — impassable pits especially must not
partition the walkable graph.

## Doodad palette (placeholder now, bespoke art later)

Districts ship with **placeholder doodads** drawn from the existing `TileManifest`
pools — every slice is playable with zero art. Sharper silhouettes are a later
art pass (slice 5); the palette below is the shopping list to generate against,
so the decorative read can be filled in without touching gen code. **All
pure-visual** — none of these affect nav / cover (that lives in the filler's
cell stamps, per "identity lives in the nav layout" above).

| District    | Doodad silhouettes to author                                        |
|-------------|---------------------------------------------------------------------|
| Spaceport   | parked crate-stacks, fuel bowsers, tug/loader, pad-edge light masts |
| Mining      | headframe / pithead tower, conveyor segments, ore heaps, skip carts |
| Refinery    | cylindrical storage tanks, pipe-run segments, flare stack, valves   |
| Agriculture | grain silos, greenhouse frames, irrigation booms, crop rows         |

Until then these read as generic crates / grates / rocks from the shared sheets.

## Cross-refs
- [`../../campaign-battle-bridge/overview.md`](../../campaign-battle-bridge/overview.md)
  — this is consumer #2; slice 0 extends `TargetProfile`.
- [`../overview.md`](../overview.md) + the `bsp/fill/` filler family
  — the content layer this expands.
- [`../stories/structural-taxonomy.md`](../stories/structural-taxonomy.md)
  — the `TacticalRegion` segmentation will read these new districts for free
  (a pit-ringed mining block is just a low-enclosure, low-cover region).
