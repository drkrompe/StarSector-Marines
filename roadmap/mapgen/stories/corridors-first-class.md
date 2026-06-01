# Story — Corridors as first-class connective structure

> The real blocker for station / ship interiors. BSP today treats leaves
> as atomic buildings and roads as perimeter filler; stations need
> corridors that *connect* rooms as first-class structure. Scoped from
> the audit ([`../pipeline-audit.md`](../pipeline-audit.md) §
> "Corridors — the bigger blocker for stations").

**Status:** design converged (2026-06-01), not yet implemented. Parked
behind the shipped room-purpose refactor (Slices A–D) and the composable
pipeline (context + stages + recipes, all shipped). This is the next
abstraction the map-gen pipeline needs before station fills can land.

## Problem

BSP leaves are atomic buildings; roads are perimeter filler. The deeper
mismatch: a **ground compound is open-with-obstacles** (connectivity is
free — units path around building islands through inherently-walkable
ground; walls are the exception), whereas a **station is
enclosed-with-passages** (walls are the default, passable space is the
exception — if you don't explicitly carve a corridor between two rooms
they're disconnected). BSP-as-used assumes the *between* space is
walkable; stations assume it's solid. That inversion — not the leaf shape —
is why the city idiom doesn't transfer.

Three concrete gaps it produces:

1. **No corridor abstraction** — road frames connect things implicitly
   but aren't marked "corridor" vs. "street," and have no width/purpose.
2. **Doorways uncoordinated** — `BuildingShellCore` places doorways
   independently; two rooms' doors don't deliberately meet across a gap.
3. **No inter-room connectivity graph** — `RoadGraphBuilder` skeletonizes
   the road mask with no concept of "this edge connects room A to room B."

## Design (converged this session)

### It's a recipe, not a new orchestrator

The classic BSP-dungeon algorithm (partition → place a room *inside* each
leaf → connect sibling rooms with corridors along the tree) already
produces exactly the rooms-and-corridors structure stations want. Our BSP
just uses the *city idiom* (leaf = filled block, cut = street). So the
station is the **textbook use of the same `Bsp.partition()` primitive**
with a different connect strategy — addressable as a `GenRecipe` over the
now-complete stage machinery, not a from-scratch orchestrator.

The one thing genuinely fused-and-needs-extracting: today **connectivity
is baked into the partition** — `Bsp.partition()` reserves a road strip in
every cut ("connectivity falls out by construction", `Bsp.java:18`). The
seam is to **pull road/corridor carving out of partition into a stage**
that consumes the leaf graph. Then:

- **City recipe:** cuts → road strips (existing behavior), fill whole leaf.
- **Station recipe:** cuts → walls; place a room inside each leaf; a
  `CorridorStage` carves corridors along a chosen subset of leaf-adjacency
  edges.

`LeafAdjacency.compute()` **already exists** (cardinal adjacency graph over
leaves; the city uses it to grow compounds). That graph is the substrate:
**rooms = vertices, corridors = a carved subset of its edges.** The
doorway-coordination problem dissolves — the `CorridorStage` owns both
endpoints, so it places each door where the corridor meets the room.

### Model (B): spanning spine, not a regular grid

Two ways to use the adjacency graph; we chose (B):

- **(A) Regular grid** — carve a (narrow) corridor on *every* cut. Cheap,
  but reads as an office floor plan: every room fronts a corridor on all
  sides. Rejected — too uniform, and once corridors cost ≥2 cells of width
  it wastes a lot of floor.
- **(B) Spanning spine** *(chosen)* — carve corridors along only a chosen
  subset of adjacency edges (spanning tree + maybe a few loops). Rooms
  become dead-ends or pass-throughs off a deliberate connective spine.
  Reads as intentional; this *is* the "corridors as first-class structure"
  thesis rather than "narrow streets indoors."

### Width policy — floor of 2, and "2 is never a venue"

A 2-wide corridor is the **minimum that flows**: the pathfinder's
`OCCUPANCY_PENALTY = 2` (`GridPathfinder.java:40`) only steers units apart
if there's a parallel lane to push into. At width 1 there's no alternative
cell, the penalty is toothless, and the cheapest path *is* the stacked
one — so units conga-line / cell-stack. Width 2 gives the penalty a lane;
a squad flows through as a 2-wide column.

But a 2-wide corridor is also a **terrible fight space** (no maneuver, no
cover) — and you can't add cover, because a pillar in a 2-wide corridor
blocks one lane and re-narrows it to 1, reviving the stacking. Cover and
width-2 are mutually exclusive. So there is **no good "combat corridor"**;
the width bands are a clean dichotomy with nothing in between:

| Width | Role | Cover | Fights |
| --- | --- | --- | --- |
| **2–3** | transit | no | no — keep short, no hold points |
| **≥4–6** | arena (junction hall) | yes | yes — combat is *meant* to land here |
| (rooms) | primary combat venue | yes | yes |

Rules that fall out:

- **Floor is 2; transit corridors are 2–3 and kept short** — cap straight-run
  length before a widening or room (a long 2-wide straight is the worst
  case: dull transit + tube-shoot if a fight happens).
- **Junctions bulge.** Where the corridor graph branches (a node of degree
  ≥ 3), widen it into a 4–6-wide junction hall with cover. This produces
  the "rooms at the decision points, tubes between them" rhythm that reads
  as designed.
- **Combat-attractors are banned from transit corridors.** No
  `GUARDPOST` / hold node, no objective, no cover on a degree-2 corridor
  cell. We can't dictate where squads collide, but we place what they
  collide *over* — keep those in rooms and junctions and the AI's own
  incentives pull engagements out of the tubes (defender holds the room
  covering the door; attacker pushes the corridor; the fight resolves at
  the threshold). Why: a fight in a bare 2-cell corridor reads as a
  genuinely strange thing to see and should never be generated.

### The validation harness is the gate

`MapValidationScanTest` (built this session) is the acceptance harness:

- **Edge-aware connectivity** — currently a no-op (walls are cells today),
  but it arms the moment corridors introduce edge-based passages (doors as
  passable edges). A disconnected room trips it.
- **Doorway integrity** *(on the menu — build alongside corridors)* — every
  room reachable through a real door; no room walled off.
- **Cramped-garrison soft-finding** — already fires if a hold-point lands
  somewhere with fewer deployable cells than `garrisonSize`, i.e. in a
  2-wide tube. Add the gen rule "no hold node on a degree-2 corridor cell"
  and **promote this finding to a hard assert** so the generator literally
  cannot ship a tube fight.

## Open design lever (not yet decided)

**How the spine is chosen.** Pure spanning tree (every room reachable,
exactly one path between any two — maximally "intentional," but no
flanking routes and a single cut disconnects a subtree) vs. **tree + a few
loops** (adds alternate routes → flanking, encirclement, less of a single
chokepoint funnel). Loops cost more corridor and soften the chokepoint
identity that makes stations tense. Leaning tree-plus-sparse-loops, biased
by mission type (Assault wants flanking routes; a tight defensive Conquest
map wants the funnel) — but undecided; this is the next thing to nail.

## First vertical slice (sketch)

Prove the rooms-and-corridors model before any station-specific fills
(hangars / habitation / `RoomPurpose.CORRIDOR` + station kinds):

1. Extract road carving out of `Bsp.partition()` into a stage (city recipe
   keeps byte-identical output — verify via the existing preview/scan).
2. `StationRecipe`: partition with cuts-as-walls; place an axis-aligned
   room inside each leaf.
3. `CorridorStage`: build the spanning subset over `LeafAdjacency`, carve
   2-wide corridors connecting room doorways, label cells `CORRIDOR`.
4. Render in `BspMapPreviewTest`; gate with `MapValidationScanTest`
   (full connectivity, no islands).

Axis-aligned rooms + straight/L corridors are the real v1, not a stopgap —
non-rectangular rooms / organic corridors are a later concern, so
`PartitionStrategy` need not generalize beyond axis-aligned bbox yet.

## Risk / scoping note (carried from the refactor)

The original worry — "stations need a *different orchestrator entirely*" —
is softened by the recipe framing above: it's a recipe + a few stages over
the same partition primitive, *if* connectivity is first extracted from
`Bsp.partition()`. Keep `PartitionStrategy` and `RoomPurpose` from baking
in any BSP-only assumption beyond "axis-aligned bbox leaf" so the door
stays open to non-rectangular rooms later.

## Cross-refs

- [`../pipeline-audit.md`](../pipeline-audit.md) — the coupling points
  this story has to break (§ Coupling points, § Corridors).
- [`../next-session.md`](../next-session.md) — the `MapValidationScanTest`
  harness (validation section) that gates this work.
- [`../complete/room-purpose-refactor.md`](../complete/room-purpose-refactor.md)
  — the shipped refactor whose "Risk: corridors-as-first-class" section
  this story formalizes.
- [`station-interior-fills`](station-interior-fills.md) — the consumer
  that depends on this landing first.
- [[road_graph_design]] — `RoadGraphBuilder` centerline extraction; the
  corridor graph is the indoor analogue.
