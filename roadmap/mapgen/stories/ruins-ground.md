# Ruins ground

Status: IN PROGRESS — slices 1-7 built (6 the interior, 7 the dressing); the battle story
plays on it but has not been tuned on it. Slice 4 of `ruins-expedition-fork.md`.

Written: 2026-11-09

Updated: 2026-11-09 — the dead town is weathered and the vault dressed (slice
7). Earlier: the interior is built: the vault's end of the delve is a
roofed megastructure (a bunker on scattered ground). Earlier the same day: Ruins
battles play on this ground, and dead streets break up into earth and rubble;
the ground is generated at every grade and keeps the delve's laws on every
surface.

Read `mapgen-nouns.md` (Grown settlements, Ruins ground, Authored damage
layers, and the ruined-lot rule), `settlement-off-map-link.md`, and
`ruins-expedition.md` (Shape of the battle) first.

## Goal

A ruins site gets its own ground: an abandoned settlement with no road out and
no supply link (`SettlementLink.NONE`), laid as a **delve** from a landing area
at one end to a vault at the other through the grade's **layers**.

## How a mission asks for it

```java
RuinsGroundPlan plan = RuinsGroundPlan.forLayers(grade.ordinal() + 1);
MapResult map = generator.generateRuins(seed, profile, plan);
RuinsDelve delve = map.ruinsDelve;
```

- `RuinsGroundPlan.forLayers(n)` states the depth, 1 (scattered) to 4 (vast),
  and from it the map size (112x64, 144x80, 200x112, 280x168) and the dead
  settlement's density. Mapgen takes the layer count, not the campaign's
  `RuinsGrade`, so world generation stays free of campaign types.
- `generateRuins` is on `MapGenerator` (`BspCityGenerator` implements it). The
  `profile` gives the world's surface and nothing else.
- The seed is the site's: the same site makes the same ground every time
  (`ruins-expedition.md`, Retry meets the same ruins).
- A ground that breaks a delve law throws; there is no fallback map.
- `BattleSetup.createRuins` does exactly this (the campaign's `RuinsDelve.layers`
  is the count), and `RuinsGroundLayout` reads the vault's source and a lift
  point per landing area and court, each with its route from the vault, into
  `RuinsVaultObjective`. No parked vehicles and no emplacement scatter on a
  dead site.

## Published places

`MapResult.ruinsDelve` (`RuinsDelve`, null on every other map). `places()`
lists every name with its area; `area(name)` answers one.

| Name | What it is |
|---|---|
| `ruins.landing` | The cleared landing ground at the west end. Its two arrival areas (four berths) are `delve.landing()` and also the map's own `landingAreas` and `landingPads`, the only berths on the map. The company spawns on it. |
| `ruins.vault` | The vault suite at the east end: `door`, `innerDoor`, `approach` (the open cell outside the door) and `source`, the one cell the breach is held on. The opposing anchor stands at the approach. |
| `ruins.layer.N` | Layer N (from 1, nearest the landing): its holdable ground, from just in front of its wall line to the next layer's; `kind` is `COLLAPSED_STREET`, `BREACHED_WALL` or `SEALED_GATE`; `lineLeft..lineRight` is the wall line. |
| `ruins.layer.N.way.M` | The open ways through layer N, low y to high: `GAP`, `BREACH`, `GATE` (a sealed gate's span, open ground until the works shut it) or `SIDE` (the way round a gate). Each has its `span` on the line and a `near` and `far` cell. |
| `ruins.layer.N.room.M` | Two small ruined rooms behind each layer, one door each: where caches and sealed stores go. |
| `ruins.layer.N.court` | The open landing court of every layer past the first, with one arrival area (`court().area()`). Not in the map's `landingAreas`: the boat is called to it. |
| `ruins.works.power-hall` | The generator hall, in a layer before the last (the only layer on scattered ground); `console` is the cell infantry hold. |
| `ruins.works.junction.N` | One junction box per layer, in the wall just past its first open way's passage; `stand`, on the passage's last row, is the cell infantry work it from. |
| `ruins.works.records` | The records office in the first layer; `console` is where it is read. |
| `ruins.interior` | The roofed part of the site: from the wall line of layer `interior().firstLayer()` to the east edge (or, when that is the layer count, the bunker round the vault). `entrances` are its ways in from open ground: the first interior layer's open ways, or the bunker's seal door. `layers().get(N).court` inside is a light well open to the sky. |
| `ruins.interior.chamber.M` | The interior's chambers off its corridors, besides the published rooms: unnamed ground for the mission to use. |
| `ruins.works.collapse` | The collapse: the short way through the last layer, in line with the vault's door, choked by a breachable run of wall. It is also a way of that layer, of kind `COLLAPSE`; the layer's open ways are the long way round. |

`liftPoints()` is the landing's arrival areas, then each court's. Which court is
held, which rooms are stores and which branch a junction feeds are the
mission's to decide (`ruins-works.md`).

## Slices

1. **The ruin as a settlement.** Built. The settlement grows at the plan's
   density with `SettlementLink.NONE`; every lot is relabelled a ruined lot
   (`RuinedLotsStage`), nothing is claimed as a compound, and the streets end in
   rubble six cells short of the map edge, so the road graph has no off-map
   entry.
2. **Landing area and vault.** Built. The landing ground at the west end, the
   vault suite at the east end in dark cast concrete with a forecourt, its one
   source cell inside the inner room.
3. **Layers.** Built. Each layer is a wall line edge to edge, jogging a cell
   between short runs, with its ways; one or two open ways on scattered ground,
   two or three at every larger grade, so no single blocked way closes the
   route there.
4. **Side rooms, courts and works sites.** Built. Two rooms behind each layer,
   a court behind every layer past the first, the power hall with generators
   and a console, a junction box per layer, the records office and the
   collapse.
5. **Pools.** Built. `RuinsGroundEvidence` (`generatedSpaceEvidence`) generates
   every grade on every surface over six seeds: every map keeps the delve's
   laws, no road leaves it, and it has no berth but its landing's. Ruins ground
   has no story-map pool: a site's seed names its map. `storyMapPools` holds
   unchanged. The look frames are `createSnapshots -Psnapshot=ruins-ground`.
6. **Interior.** Built: the megastructure, or the bunker, at the vault's end
   (Interior, below). The battle's look frames, with an interior frame with its
   roofs off, are `createSnapshots -Psnapshot=ruins-battle-look`.
7. **Building dressing.** Built. A ruins site is longer dead than a town's
   freshly ruined lots, so `RuinsDelveStage` weathers the dead settlement
   before laying the delve, by a coordinate hash: the lots' floors (the
   coloured footprints that read as roofs from above) break into rubble and
   earth in jittered patches about three cells across, a fifth of the old floor
   showing through; standing walls and stubs in the olive sheet, which reads as
   a hedge, become masonry; two loose furnishings or stores in three are gone,
   half of those left as scrap. The vault is dressed as the site's hardened
   heart: buttresses at its corners and along its faces, the vault room in the
   command floor, the haul crated in its corners, a console in the
   antechamber. Interior rooms are built of the hull (slice 6). Weeds were
   tried and dropped: a loose tuft is not drawn on rubble.

## Open

- **Small grades put the vault in open country.** The dead settlement grows
  about the map's middle, so on scattered and widespread ground the vault's
  end is open country with a few rooms; at extensive and vast the vault stands
  deep in the ruins. Growing the settlement toward the vault is the lever if
  the owner wants ruins round it at every grade.
- Layer and room sizes, densities and court counts are first guesses
  (UNTUNED), to be tuned once the battle plays on the ground.

## Interior: outside, then inside

Owner, 2026-11-09: some ruin sites are megastructures or bunkers the company
explores. The company lands outside, works into roofed rooms, corridors and
seal doors with the vault deep inside, and brings the haul back out.

- **What grows with the grade** (`RuinsGroundPlan.interiorLayers`). Scattered
  ruins have a bunker round the vault: its forecourt roofed as an antechamber,
  a hull ring, and one seal door on the vault's row. At widespread the last
  layer is inside; at extensive and vast the last two. The landing, every
  court and the layers before the interior stay outside.
- **How it is carved** (`RuinsDelveStage`). From the first interior layer's
  wall line to the east edge, everything but the ways' passages and the vault's
  apron is filled with hull (the installation's dark cast concrete, the vault's
  own material); the layer lines inside become its walls and bulkheads. Each
  interior band gets its court first, then a spine corridor running the band's
  length through it, a corridor from every way into the spine and out of it,
  and two doors from the last band into the vault's hall. The band's rooms and
  power hall, and chambers scaled to its area, are joined to the spine by the
  shortest corridor through the hull. The station recipe's room carving was
  not reused: the delve needs its own published places and layer lines, so it
  carves round them.
- **Roofs.** Every carved cell is a building floor, so the flood fill makes the
  whole interior one roofed building, and a court inside, with no building on
  it, is a light well open to the sky. A roof covers a building's floor and the
  walls beside it, so `RuinsRoofStage` registers the solid hull (every hull
  cell no open cell touches) as one more building in the interior's tint: one
  slab roof runs over the whole megastructure, and going in lifts the roof over
  its halls while the mass stays roofed. Rooms and the power hall inside are
  built of the hull, not the dead town's materials. `RuinsGroundEvidence` holds the vault,
  every interior room and every chamber under a roof, and every lift point
  under open sky.
- **The power hall** stands inside at extensive and vast (the first interior
  layer, a layer before the last); at widespread the only layer before the last
  is outside, so it stays there.
- **Laws.** `DelveCheck` reaches every chamber and every entrance; the layer
  laws (no single blocked way closes the route) hold on the interior's
  bulkheads as on the lines outside.
- **Roof decided** (owner, 2026-11-09, picks `ruins-interior-roof`): a, one
  roof over the whole megastructure in the existing roof art, over a dedicated
  megastructure set (mocked with the plate row) and strips over the halls.
  `createSnapshots -Psnapshot=ruins-roof-options` renders the first two.
- **Open.** The chamber count, spine width and hull material are UNTUNED. Seal doors are the works'
  (`ruins-works.md`); the ground publishes the gate spans and the bunker door
  they shut.

## Constraints

- Ruins are a settlement value, not an exception: no law gains a carve-out.
- No new hazard system. The ground is terrain.
- Art reuses the ruined-lot and rubble materials; a new wall or floor set is a
  separate art decision with picks.

## Done

The battle story plays on this ground at every grade. Fold the rules into
`mapgen-nouns.md`, add a row to the mapgen `shipped.md`, and delete this story.
