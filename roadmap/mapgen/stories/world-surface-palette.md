# World surface palette

Status: IN PROGRESS — the palette reaches wild ground and the wild/cultivated
line is pinned; worlds beyond rock, dust and ice are open.

Written: 2026-08-31
Updated: 2026-08-31 — cultivated ground pinned by `CultivatedGroundTest`; the
ice case closed by the `FROZEN` palette and the `floors.snow` plate.

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

## What is open

**Only ROCK, ARID and FROZEN are authored.** Toxic, irradiated, volcanic and
ash worlds all collapse into `ROCK`. Splitting them is a JSON block each plus a
material family, once the art supports a visible difference.

`FROZEN` is what that costs end to end, and it is worth writing down because it
is the template: eight tileable materials, a `floors.snow` variant pool on eight
free cells of the Floors plate, a `groundRender` line, a `surfaceFillers` block,
a `SurfaceZoning` family, and a re-derive of the plate's `_height` / `_normal`
companions. Only the first of those is slow.
