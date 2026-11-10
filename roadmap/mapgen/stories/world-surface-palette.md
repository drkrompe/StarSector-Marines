# World surface palette

Status: IN PROGRESS — the palette reaches wild ground and the wild/cultivated
line is pinned; lava worlds have their own ground; toxic and irradiated worlds
are open.

Written: 2026-08-31
Updated: 2026-11-09 — lava worlds have their own ground and the living worlds looks of their own (`LivingWorld`);
remaining work is distinct toxic and irradiated material families.

## What this is

Map generation assumed Earth. Open ground was grass because
`NATURE_GRASSLAND` was the filler that happened to exist, which baked
habitability into a stage that should have no opinion about it. This is a space
game whose settlements sit on barren rocks, ash plains and irradiated waste far
more often than on anything green.

A **surface palette** is what the world's own ground is made of, distilled from
the target planet at the campaign boundary. It is the terrain counterpart to
`EconomicFunction`: a vanilla fact turned into a game-API-free enum by a policy
class at the resolver, carried on `TargetProfile`, read by stages through
`BspKeys.MARKET_PROFILE`. `battle.world.gen` never sees a `PlanetAPI`.

`SurfaceZoning` owns what a planet type id means, and takes a plain string so it
stays testable without a sector. Ids match by substring, because vanilla and
mods both spell families out with suffixes — `barren-bombarded`,
`barren_castiron`, `lava_minor` — and enumerating variants would silently miss
the next one.

## The wild / cultivated line

**The palette governs ground nobody planted, and only that.** A settlement's
parks and street verges are ground the colonists made and maintain; irrigated
lawn on an airless rock is a statement about the colony's wealth, not about the
planet. So cultivated fills keep their own character and never consult the
palette, while the hinterland and the wild `NATURE_*` lots follow the world.

This is the same distinction the biome law already draws — *a biome is the
ground a place is built on, not the ground a place is made of* — applied one
level further in. A lawn is ground a place is made of.

## Rock is the baseline, and that is a deliberate change

`TargetProfile.NEUTRAL` carries `ROCK`, not a living world. Surface is the one
field that cannot read as "no signal", because ground has to be made of
something, and defaulting to grass quietly asserts habitability on every map
that has not said otherwise. An unknown or modded planet type, a station with no
planet behind it, and a battle with no world at all all read as rock.

This does change wild ground on maps that previously came out green, which is
the point.

## Keyed by palette, not by more block kinds

`GenMappingRegistry.fillerParams(BlockKind, SurfacePalette)` falls back to the
palette-free entry when a pair is unauthored, so a palette declares only the
kinds it actually changes and every existing filler keeps working untouched.
The palette-free `NATURE_GRASSLAND` entry *is* the verdant one, so `VERDANT`
needs no JSON at all.

The alternative — a `NATURE_REGOLITH` / `NATURE_ASH` / `NATURE_ICE` per world
type — was rejected. A barren world does not want a new block kind; it wants
`NATURE_GRASSLAND` to mean something else. Multiplying nature kinds by world
types is 3xN constants and forks every district weight table for a difference
that is only ever material.

## A fault this uncovered

`NatureZoneFiller.baseTileIdFor` mapped only GRASS, DIRT, SAND and WATER to a
representative tile, returning null for everything else — and a null base means
the cell hosts **no overlay at all**, silently. A rock palette built on STONE
would therefore have scattered no rocks. Stone and rubble now resolve to
`nature.dirt-1` for legality only: rocks accept any ground-layer tile and plants
require grass specifically, so a stony surface correctly keeps its rocks and
correctly grows nothing.

## Volcanic: rock's layout in other art

`VOLCANIC` is the vanilla `lava` and `lava_minor` worlds. It is the first palette
that differs from another **only in what the ground is made of**, and it says so:
`SurfacePalette.layoutOf` names the palette whose generated layout it shares
(`ROCK`). Every generation decision that reads the palette treats it as rock —
the crater-rim hills, the formation draws, the field stones, the fillers
(`GenMappingRegistry.fillerParams` falls back to the layout's entries) — so a
seed makes the same map on either. That is what lets a lava world play the
story maps already vetted for rock (`story-map-pools.md`) instead of a pool of
its own, and it makes a control render exact: the same seed as rock and as
volcanic differs in nothing but art (`volcanic-ground-look`).

What differs, all presentation:

- **Ground.** Its stone draws as cooled ropy basalt and its sand as settled ash
  (`paletteGroundRender` in the mapping: a world's own material for a ground
  kind, over the kind's `groundRender`). The cell stays `STONE` or `SAND` for
  every rule. The art is the `volcanic-ground` sheet, eight wrapped variants of
  each from ImageGen masters (`art-source/tilesets/volcanic-ground/`). Plaza stone
  in a volcanic town draws as basalt too: building with local stone.
- **Hills.** `TerrainPlan.Finish.BASALT` (`Finish.of(palette)` is the one
  rule for a world's finish): the lava rift's columnar basalt wall for the faces,
  the flow for the summit (`basalt-terrain`). The craters read as cinder cones.
- **Formations.** `BASALT_BOULDERS` and `BASALT_SPIRES` share the rock
  formations' bodies exactly; their art is the rock sheet regraded, with ember in
  the cracks of the damaged and destroyed states (`volcanic-formations`).
- **Glow and ash.** `VolcanicGroundRenderSystem` draws, additively over the
  tiles, ash drifts in the hollows and glowing fissures, vents and embers in
  active fields, each casting the lava rift's light (`LavaVoidLook`) and
  breathing. Where they go is `VolcanicGroundLook`'s: only open wild stone or sand
  with open ground about it, never under a wall, road, raised ground, fixture or
  authored surface. Coordinate-hashed; nothing gameplay reads.
- **Storm drift** banks ash. The field stones are the scoria set
  (`RockDamage.Biome.SCORIA`): the basalt stones graded dark and faintly warm by
  `nature-tiles-refit.py`, in the same pool sizes and chances as rock, so they
  fall where rock's would.

Known left: raised ground from `ElevationBrush` picks its finish from the
ground kinds and so stays rock on a lava world; no glow feathering at the ground
target's edge, since the basalt joins no feathered material.

## What is open

**Living worlds are one palette with four looks.** Jungle, tundra and ocean
worlds are `VERDANT` with a `LivingWorld` look rather than palettes of their own
(`mapgen-nouns.md`, a living world has a look): every rule the palette owns is
the same for them, and only what grows and how wet the ground is differs.

**Toxic and irradiated worlds still use `ROCK`.** Splitting each is a palette
plus a material family; where its layout is rock's, `layoutOf` and
`paletteGroundRender` make it an art change only, as volcanic is.

Types that share a palette are told apart by a surface signature rather than by
more palettes (`mapgen-nouns.md`, surface signatures): cryovolcanic, bombarded,
venus-like, unstable, cast-iron and metallic worlds each wear one. `toxic_cold`
has none yet; it waits on the toxic palette.

`mapgen-nouns.md` owns planet terrain and mineral-field behavior. Additional
palettes must select appropriate permanent masses and destructible obstacles
alongside ground materials, preserving their separate gameplay contracts.

`FROZEN` is what that costs end to end, and it is worth writing down because it
is the template: eight tileable materials, a `floors.snow` variant pool on eight
free cells of the Floors plate, a `groundRender` line, a `surfaceFillers` block,
a `SurfaceZoning` family, and a re-derive of the plate's `_height` / `_normal`
companions. Only the first of those is slow.
