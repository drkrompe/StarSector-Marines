# Owned maps

Status: PARKED (owner, 2026-11-10: "We should start small. Each mission should have a single seed per biome. Once we
get that right then we can branch out with more variety."). Owned maps are that later variety step. Until it resumes:
the document, `OwnedMapJson`, `OwnedMapCatalog`, `BiomeSkin` and `OwnedMapRecipe` stay on main as built, and Relay
Nine keeps reading `relay-nine.map.json` (its map was code-authored, never seeded, so it already is one map on every
biome and serves the one-seed stage as it is). Nothing else converts; the map editor builds no Maps page on this
format; the sealed-town extraction (`SealedTown` for a lot) and a public drop cutter (`NaturalChasm`) offered by
their sessions were declined. Resuming starts at Not yet, below, with the map inventory's order
(`G:\My Drive\starsector\review\owned-maps\2026-11-10_mission-inventory_win\inventory.md`).

Written: 2026-11-10

`mapgen-nouns.md` is the model this sits in. Read with `mission-layout.md` (pieces a generated map lays for a
mission), `story-map-pools.md` (the seed pools this replaces for story maps) and `voids.md` (natural drops).

## Why

A story map used to be a vetted seed per biome. A seed names a different layout on every palette, so the "same"
mission was a different map on a jungle and on an ice world, and a person could own a map only one biome at a time.
The owner wants the opposite (2026-11-10): "make fun maps, then skin them to match the biomes". A mission owns one
layout, authored once and tailored to it. The world decides how that layout is dressed: the ground, how its drops
look, and how its settlements are built to live there. All the palette, drop, signature and habitat work becomes the
skin and the editor's palette, not random generation.

## The owned map

An **owned map** (`OwnedMap`) is one mission's layout, a data document at `data/world/maps/<id>.map.json`
(`OwnedMapCatalog`, installed at application load through `SettingsAPI`; `OwnedMapJson` reads and writes it, and a
written map reads back equal, which the editor relies on). It holds **form and purpose only**:

- **Extent, origin and objective.** The origin is where the attacker arrives. Every route is proved from it.
- **Roads and inaccessible terrain**, by reference to their own documents (`data/world/roads/`,
  `data/world/terrain/`), so the Roads and Terrain pages keep editing them. The road graph starts from a named road.
- **Grounds** by role: a `YARD` (a works' hard standing, round-cornered by a radius) and a `MARKING` (painted
  standing). The role says what the ground is for, the skin what it is made of.
- **Pads**: landing pads with their approach and purpose.
- **Sites**: the mission's named places, each a tactical node. A site on a building takes the building's anchor and
  box once it stands, so a mission never states a cell inside a building.
- **Settlements** by **program and role**, never by building style. The program ("power-relay", "market-town") names
  it for people. The role (`TOWN`, `WORKS`) bounds what a skin may make of it. Each settlement also has:
  - a **lot**: the ground it owns;
  - **gates**: where its ways meet the lot's edge;
  - named **buildings**: an authored plan (`BuildingStamp`) or a facility shell, each where the map puts it;
  - its **works**: yards, fences, bays and parked machines, in order;
  - optionally a drop it may **terrace into**.
- **Walls** and **props** set down by hand.
- **Drops** by role (`RIVER`, a watercourse; `RIFT`, a deep split). Each drop is a path, a width, decked crossings,
  and optional depths along it as shares of the skin's band.
- **Rises** by role (`BANK`, a gorge floor's terraced bank): a box and a height as a share of the skin's drop floor.
- **Routes**: cells beyond the pads, sites and buildings that must stay reachable.

Nothing in the document names a material, a drop style, a wall surface or a habitat.

## The biome skin

A **biome skin** (`BiomeSkin`) is a deterministic reading of the target world: its palette, living world, signature
and climate (`BiomeSkin.of(TargetProfile)`, or `forPlanetType` from a vanilla type id and climate). It answers every
question the map leaves open:

- **Ground**: the wild ground every cell starts as (`SurfacePalette.inlandGround`: lawn on a living world, the
  palette's own ground elsewhere); a yard's concrete; the terrain masses' finish (`TerrainPlan.Finish.of`).
- **Drop style**, from the drop's role. On a volcanic world a `RIVER` is the 4 m lava river and a `RIFT` the lava
  rift. Every other world takes its palette's one drop for both roles (`NaturalVoidZoning.forPalette`):
  - gorge river on a living world;
  - canyon on an arid one;
  - crevasse on a frozen one;
  - dry gorge on rock and irradiated worlds;
  - toxic gorge on a toxic one.
  A depth share is metres within the style's band. A style with one depth keeps it whatever the map asks. A map has
  one void look, taken from its first drop's role.
- **Rise style**: ice banks on a frozen world, rock strata elsewhere.
- **Signatures and nature**: the planet type's detail (vents, craters, haze, fissures, metal) and its living world's
  dressing, laid by the ordinary landscape pass over open country.
- **Habitat** (`Habitat`): shielded under hard radiation or on an irradiated world; sealed where the air is toxic or
  absent, or on a toxic world; insulated on a frozen world; open elsewhere. The palette answers when the climate is
  silent (a story map or a fixture has no market behind it), so such a map still adapts.
- **Adaptation** of each settlement (below).

### Adaptation

How a settlement is built to live where it stands (`BiomeSkin.adaptation`). A jungle colony with breathable air does
not lay itself out like a colony on a toxic world, where touching the air kills:

| Adaptation | When | What it builds |
|---|---|---|
| `SEALED_PERIMETER` | the habitat is sealed or shielded (toxic, airless, irradiated); wins over everything | the habitat's shells and locks, and a perimeter wall on the lot's edge, open only at locks |
| `CREVASSE_TERRACE` | the settlement may terrace into a drop and this world's drop takes terraces (a crevasse) | the settlement terraced into the drop's bank, out of the wind |
| `INSULATED` | a frozen world | brick and steel shells; its people still walk between buildings |
| `OPEN` | otherwise: breathable worlds such as jungle, verdant and ocean | the town as the map lays it |

A **sealed perimeter** is one continuous wall with a few locks, as a sealed town is (owner, 2026-11-10: a large town
sealed within, fewer doors to get wrong; `SealedTown` on generated maps). It stands on the lot's edge, never inside it. It yields wherever the ground is not open: a road
(which runs through as a lock), a building, a pad and the ground before it, a doorway's step, a drop. It leaves a
lock two cells either side of every gate, with the floor marked. A side with nothing to yield to and no gate gets a
lock at its middle, so it never seals what it encloses. Today's wall is the stand-in in `OwnedMapRecipe.perimeter`,
in the habitat's shell. The sealed-perimeter town built for toxic, airless and irradiated worlds replaces it inside
the same lot and gates.

A **crevasse terrace** is decided by the skin. Laying it waits on the crevasse town's terrace work (`voids.md`, the
crevasse town; `BuildingStamp.TERRACE_BLOCK` and `TERRACE_ROW`). Until it lands, such a settlement is built
insulated.

## Rules

**What a skin may change:** every material (ground, yard, wall shells, terrain finish), the drop's style and its depth
within the style's band, the rise style, signatures, the nature that grows on open country (woods, scrub, field
stones), a habitat's thresholds and courts, and a sealed settlement's perimeter on its lot's edge.

**What a skin never changes:** where any building, door, window, wall, road, pad, site, drop cell, crossing or rise
cell is. It also never touches a lot's interior form or a gate. A lot is **held ground**, so the landscape grows nothing
on it and every world builds it on the same ground. Nature may block open country only outside held ground, the
landscape's own reservations (roads, pads, made ground, anchors) and every site's cell.

**One check.** `OwnedMapRecipe.result` proves every pad, site, building and listed route reachable from the origin on
the skin it was built for, and refuses an obstructed berth. A skin that strands anything fails the build, naming the
cell.

**Drops yield to nothing built.** A drop over a road leaves the road standing as a bridge. A drop over a building,
wall, prop, pad or terrain mass refuses the map, naming the cell: the author redraws it.

## The build

`OwnedMapRecipe.build(map, profile, seed)` runs one fixed order whatever the skin:

1. Wild ground, then yards, their concrete, roads, pads and markings.
2. Buildings by settlement, in the document's order.
3. Walls, props, and the replaced road surfaces cleared.
4. Yard concrete, then each settlement's works.
5. Terrain masses, with the pads' approaches kept clear.
6. Adaptation (a perimeter yields to the cells the drops will take), then finalize.
7. Drops and rises, after finalize as on a generated map, so they are natural ground and never walls.
8. The landscape round the origin, the objective and every site's cell.

The seed draws only the landscape's nature. A drop's look and depth spread come from the map's id, so the layout and
its drops are the same on every run. `Built` gives the mission its context (for ground of its own, such as Relay
Nine's prepared positions), its sites as nodes in the document's order, and each named building's place.
`OwnedMapRecipe.result` then seals the map.

## Relay Nine, the first

Relay Nine was converted first because the existing tools made it the easiest:

- Its map was already authored rather than seeded, on its own road and terrain documents.
- It has named sites on named buildings.
- It has no market-sized town to program.
- It is a live mission with a short setup (`BattleSetup.createRelayNine`).

The conversion laid the code-authored map cell for cell on rock, arid and verdant: the same ground, surfaces, walls,
props and sites. The owned map then gained a **south wash**, a `RIVER` drop between the two southern escarpments
below the reserve shed, which the reserve road crosses as a bridge. It lies outside the main fight and shows each
world's drop: a gorge river, a canyon, a crevasse, a dry gorge, a toxic gorge, or the shallow lava river. On a frozen world the relay hall's ceramic walls turn to brick (insulated), and on airless, toxic and
irradiated worlds the works is sealed. `createSnapshots -Psnapshot=owned-map-look` shows it under every palette.

## Not yet

- The editor's Maps page edits the document (the map-editor session).
- The sealed-perimeter town and the crevasse terrace replace their stand-ins. The stand-in perimeter leaves a gap
  where a building's door stands on the lot's edge rather than wall it shut; until the editor warns of it, keep a
  lot's edge off doors (Relay Nine's switch house and relay hall still touch theirs).
- On a volcanic world a `RIFT` stays the deep lava rift though generated maps now default to the lava river.
- Conversion order for the other missions: the map inventory's list.
- A story-map pool entry may name an owned map instead of a seed. Once a pooled mission is converted, its pool retires.
