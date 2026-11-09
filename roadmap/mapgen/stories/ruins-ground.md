# Ruins ground

Status: IN PROGRESS — slices 1-5 built; the battle story has not played on it
yet. Slice 4 of `ruins-expedition-fork.md`.

Written: 2026-11-09

Updated: 2026-11-09 — Ruins battles play on this ground; dead streets break up
into earth and rubble; the owner's hybrid direction (land outside, explore an
interior) is the next slice. Earlier the same day: the ground is generated at
every grade and keeps the delve's laws on every surface.

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
| `ruins.works.junction.N` | One junction box per layer, beside its first open way; `stand` is the cell infantry work it from. |
| `ruins.works.records` | The records office in the first layer; `console` is where it is read. |
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

- **What grows with the grade.** The interior is the delve's far end, sized by
  the layer count: scattered ruins have a small bunker round the vault (the
  vault suite and an antechamber or two behind one seal door); at widespread
  the last layer is inside; at extensive and vast the last two. The landing,
  every court and the layers before the interior stay outside, so every lift
  point is open sky.
- **Built from what exists.** The station recipe already carves rooms and
  corridors out of a solid hull (`RoomCarveStage`, `CorridorStage`, with its
  room graph); the delve would carve that inside a megastructure footprint at
  the east end, in the installation's concrete, rather than a new wall set.
  Roofed cells, doors and the building flood fill are what make it an
  interior to flocks, fire and fog.
- **Its places.** An interior layer publishes the same names (ways become
  doors and seal doors, rooms are rooms), plus `ruins.interior` for its
  footprint and its entrances as ways of the first interior layer. The works'
  seal doors and power hall stand inside.
- **Laws.** The delve laws hold through the interior: no single blocked door
  closes the route at the larger grades, and every interior room is reachable
  from an entrance.

## Constraints

- Ruins are a settlement value, not an exception: no law gains a carve-out.
- No new hazard system. The ground is terrain.
- Art reuses the ruined-lot and rubble materials; a new wall or floor set is a
  separate art decision with picks.

## Done

The battle story plays on this ground at every grade. Fold the rules into
`mapgen-nouns.md`, add a row to the mapgen `shipped.md`, and delete this story.
