# Story — Structural taxonomy for the city (generator publishes, passes consume)

> The cross-cutting direction from [`../overview.md`](../overview.md) §
> "Structural taxonomy", scoped to the **city** generator as its first real
> implementation. Generalizes the room-purpose seam from "which room is the
> throne" to a vocabulary the whole map publishes and placement passes query.

**Status:** design converged (2026-06-01), Lever-1 foundation in progress.
Parked behind the shipped room-purpose refactor + composable pipeline.

## The premise we had to throw out

The corridors story sketched the taxonomy over a **connectivity graph** —
room degree, depth, articulation points / bridges. That framing is correct
for a **station** (enclosed-with-passages: rooms are the space, walls are the
default, a bridge corridor really is the only way through). It does **not**
transfer to the **city**, and grounding the code showed why two ways:

1. **`LeafAdjacency` is the wrong graph.** It's a *block-clustering* relation,
   intentionally **trunk-segmented** (trunks are barriers so a compound can't
   span Main Street). So it's disconnected into quadrants — BFS for an
   "assault depth" gradient can't cross a trunk, which is exactly where units
   *do* move. Articulation points in it are clustering artifacts, not
   movement constraints.
2. **The city is porous, so no graph gives real chokepoints.** Infantry
   walkable space is ~the complement of the building islands — one big
   connected blob. A `RoadGraph` "bridge" isn't a chokepoint: a marine just
   walks across the adjacent park instead of taking the street. The road graph
   is a *vehicle* abstraction over porous ground, not a movement-constraint
   graph. Topological chokepoint analysis (either graph) yields
   confident-looking roles that correspond to nothing a unit experiences.

This is the open-vs-enclosed inversion the corridors story itself names — a
station computes taxonomy over room-adjacency; the city is the *negative* of
that (streets are the space, blocks are the obstacles), and its porosity means
**texture, not topology**, is the readable signal.

## The reframe — two levers

**Lever 1 — read the *texture* of the porous space.** *(this slice)*
Even with no chokepoints, open space isn't uniform. Segment the walkable blob
into **tactical regions** and tag each with attributes that don't depend on
global connectivity:

- **kind** — `STREET / PLAZA / COURTYARD / OPEN_GROUND / RUBBLE /
  BUILDING_INTERIOR`, mapped from `GroundKind` (the fillers already paint the
  distinction; we're naming it).
- **cover density** — fraction of the region's cells adjacent to a
  wall/cover cell. High = alleys/courtyards you can hug; low = killing ground.
- **exposure** — mean cardinal open-cross extent (how far you can see/shoot
  along the cardinals before a wall). High = crossroads/plaza; low = tight.
- **enclosure + opening count** — a *local* measure, robust to global
  porosity: of a region's boundary, what fraction borders non-walkable, and
  how many distinct walkable "mouths" it has. A courtyard ringed by buildings
  with 1–2 openings is a genuine defensible pocket even on a porous map.
- **assault-depth band** — geometric position along the traversal axis
  (`FORWARD / MID / DEEP / REAR`). Correct *because* it's geometric, not
  graph-derived — it crosses trunks fine. `UNSET` in legacy (no-axis) mode.

Placement then queries **attributes, not topology**: heavy emplacement →
*enclosed, mid-depth, low-exposure-with-cover*; overwatch → *high-exposure
fronting a plaza*; ambush → *high-cover-density region forward of the line*.
This is the city-correct analogue of the station's room taxonomy — the station
has discrete rooms, the porous city has continuous space we segment.

**Lever 2 — *inject* structure so topology becomes meaningful.**
*(captured future work — see [`../overview.md`](../overview.md); candidate for
its own "upgrade city generation" session.)*
Make the generator deliberately produce constraint — compound walls with
deliberate gates (generalizing `GatedHousingFiller` / the fortress), denser
quarters with real alleys, walled parks — and **tag it at carve time** (the
room-purpose precedent: labels written by the carver, not inferred). This
*reduces* porosity on purpose, so enclosure/gates become real chokepoints
worth defending. The key dependency: even after Lever 2, you still need Lever
1's region+attribute vocabulary to *tag* the structure you inject. Lever 1 is
the foundation either way; Lever 2 is the content that makes it bite.

## This slice (Lever 1 v1) — the `TacticalRegion` artifact

A behavior-preserving foundation, mirroring room-purpose Slice A: compute +
publish + preview + validate, **no consumer yet** and **no generated-output
change** (pure analysis, draws no `rng`).

- **`battle.world.gen.taxonomy`** (new, generator-agnostic):
  - `RegionKind` (+ `fromGround(GroundKind)`), `DepthBand`.
  - `TacticalRegion` — id, kind, bbox, centroid, area, the attributes above.
  - `TacticalRegionMap` — the flood-fill segmenter (`build(grid, topology,
    axis)`) + the `regionIdAt(x,y)` lookup grid + the region list. Pure;
    depends only on `NavigationGrid` + `CellTopology` + `TraversalAxis`.
- **`TacticalRegionStage`** (`bsp.stage`) — runs after `FinalizeStage` (final
  walkability + ground kinds) in **both** recipes; binds the map under
  `BspKeys.TACTICAL_REGIONS`. No `rng` draw → byte-identical maps.
- **`BspCityGenerator.getLastTacticalRegions()`** — preview/analysis accessor,
  like `getLastBiomeMap()`. **Not** plumbed into `MapResult` yet — that lands
  with the first runtime consumer (a deliberate scope line).
- **`TacticalRegionPreviewTest`** — colors regions by kind + an enclosure/
  exposure heat overlay; prints per-seed attribute summaries. The gut-check.
- **`TacticalRegionTest`** — segmentation invariants (every walkable mapped
  cell assigned exactly once; area matches; attributes in range), determinism
  (same seed → same regions), `enclosure==1.0` flags an isolated walkable
  pocket (an island — should never appear given the connectivity scan).

## Segmentation rules (the load-bearing detail)

- A region = a cardinally-connected run of **walkable** cells sharing one
  `RegionKind`. Non-walkable cells (walls / water / building shells) get
  `regionId = -1` and partition the blob into regions at every ground-kind
  change and every obstacle.
- The `GroundKind → RegionKind` map: `STREET/SIDEWALK → STREET`;
  `BRICK/STONE/STRIPED/LZ_MARKER → PLAZA`; `COURTYARD → COURTYARD`;
  `GRASS/DIRT/SAND/SNOW → OPEN_GROUND`; `RUBBLE → RUBBLE`;
  `INDOOR/TILE → BUILDING_INTERIOR`; `WATER` is never walkable, so excluded.
- Boundary classification per region cell × cardinal neighbor: **closed**
  (OOB or non-walkable), **mouth** (walkable, different region), **interior**
  (same region). `coverDensity = cellsWithAnyClosed / area`;
  `enclosure = closedEdges / (closedEdges + mouthEdges)`; `openingCount` =
  connected runs of mouth-bearing boundary cells (distinct entrances).
- `exposure` precomputed once as a global cross-run grid (four linear sweeps),
  then averaged per region.

## Membership vs. positional — the corner-tower correction (playtest read, 2026-06-01)

Eyeballing the Lever-1 previews surfaced the limit of region-level attributes:
the **enclosure** attribute is a *membership* read ("is this a holdable
pocket?"), and it is **blind to fields of fire**. The walls that make a
courtyard/interior score high-enclosure are the same walls that wall *in* a
turret's arc — so the auto-ringed "defensible pocket" is the *worst* place to
mount a gun. An enclosed pocket is valuable, but for the opposite role: a
garrison hunker / fallback / last stand (and CQB value to the **attacker** once
breached). It is defender-favorable only if you're holding the *room*, not
projecting force from it.

A defender's emplacement wants the **positional** read instead — a **corner
tower**: cover/wall at its *back*, commanding a long sightline *out* over
low-cover ground. That's the intersection of "enclosure behind" and "exposure
ahead, facing outward" — **directional and per-cell**; a region average can't
see it (an interior's *mean* exposure is low precisely because it's walled). So:

- **Membership** (region attributes, shipped) answers *where to garrison / fall
  back* — high-enclosure pockets, by `RegionKind` + depth.
- **Positional** (per-cell roles, not yet built) answers *where to mount a gun*
  — the corner tower / overwatch cell. This is the corridors-doc "positional
  axis" (threshold / overwatch / deep cover); the placement use-case shows it's
  the **load-bearing** layer, not a deferred nicety.

Two different queries → two different node kinds (don't collapse them). The
overwatch corner already exists **by hand**: `FortressWallStamper` puts
`HEAVY_TOWER`s on the attacker-facing wall edge overlooking the kill zone with
magic offsets — *that is* the corner tower. The positional layer generalizes it
so every walled compound earns perimeter towers facing out, instead of
nonsensical interior turrets.

**Sketch of the positional read** (reuses the cross-exposure grid, retaining
per-direction runs instead of summing): a candidate overwatch cell is walkable,
has a non-walkable ("cover at back") cardinal neighbor, and a long *outward*
open-run (away from that wall) over a low-cover region. Score ≈ (cover behind) ×
(capped outward run into low cover) × (corner bonus). The game already bakes
per-facing cover (`NavigationGrid.getCoverAtFacing`), so this composes with
existing cover data.

A **gut-check overlay exists** — `TacticalRegionPreviewTest` draws the
non-max-suppressed top candidates as orange arrows (firing direction) on the
kind + heat PNGs. Building it surfaced two non-obvious requirements: (1) the
map edge must NOT count as "cover at back" (else map-spanning edge sightlines
dominate the ranking), and (2) **the read needs the assault axis** — a defender
overwatch fires *toward the attacker* (decreasing depth: south for
SOUTH_TO_NORTH). Without that orientation it finds wall-backed sightlines facing
the wrong way (they clustered on the beach firing inland). Reach is capped (~14
cells) so corner quality wins over raw sightline length. The scoring lives in
the preview test for now (single caller); promote it to the taxonomy package
when the first consumer slice formalizes the positional layer.

## Future slices

1. **First consumer (Lever-1 payoff).** Migrate one placement pass to query the
   taxonomy. **Split by role, per the corner-tower correction above:** a
   defender turret / heavy emplacement wants a **positional overwatch corner**
   (cover-at-back + outward field of fire over a low-cover region), NOT a
   high-enclosure region — so this slice likely needs the per-cell positional
   read, at least a v0. Garrison / fallback nodes (`BARRACKS`, `FALLBACK_TO`)
   are the ones that want high-enclosure *membership*. `DefensePostStamper`'s
   biome-band proxy is the natural migration target; depth still tiers it.
   First player-visible change; plumb `TacticalRegion` into `MapResult` here;
   RNG-parity carefully checked.
2. **Lever 2 — structure injection (own session).** Gated courtyards / walled
   pockets / denser alleys, tagged at carve time; the artifact picks them up
   automatically (a courtyard with one gate reads as high-enclosure,
   `openingCount == 1`). See [`../overview.md`](../overview.md).
3. **Station reuse.** The station recipe's discrete rooms feed the *same*
   `TacticalRegion` vocabulary (a room is just a high-enclosure region whose
   mouths are doorways), so the corridor taxonomy and this one converge.

1. **First consumer (Lever-1 payoff).** Migrate one placement pass to query
   the taxonomy — e.g. `DefensePostStamper`'s biome-band proxy → region
   attributes (heavy posts seek *enclosed, mid/deep* regions; light posts seek
   *forward, exposed*). First slice that changes what the player sees; plumb
   `TacticalRegion` into `MapResult` here. RNG-parity carefully checked.
2. **Lever 2 — structure injection (own session).** Gated courtyards / walled
   pockets / denser alleys, tagged at carve time; the artifact picks them up
   automatically (a courtyard with one gate reads as high-enclosure,
   `openingCount == 1`). See [`../overview.md`](../overview.md).
3. **Station reuse.** The station recipe's discrete rooms feed the *same*
   `TacticalRegion` vocabulary (a room is just a high-enclosure region whose
   mouths are doorways), so the corridor taxonomy and this one converge.

## Cross-refs

- [`../overview.md`](../overview.md) § "Structural taxonomy" — the
  cross-cutting principle this implements for the city.
- [`corridors-first-class.md`](corridors-first-class.md) § "Structural
  taxonomy" — the station-shaped framing this story corrects for the porous
  city; the two converge once stations carve discrete rooms.
- [`../next-session.md`](../next-session.md) — handoff + the
  `MapValidationScanTest` harness that gates connectivity.
- [[battle_tactical_node_anchor_contract]] — the anchor/garrison contract any
  future placement consumer must honor.
