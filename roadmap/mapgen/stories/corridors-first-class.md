# Story — Corridors as first-class connective structure

> The real blocker for station / ship interiors. BSP today treats leaves
> as atomic buildings and roads as perimeter filler; stations need
> corridors that *connect* rooms as first-class structure. Scoped from
> the audit ([`../pipeline-audit.md`](../pipeline-audit.md) §
> "Corridors — the bigger blocker for stations").

**Status:** not started. Parked behind the shipped room-purpose refactor
(Slices A–D); this is the next abstraction the map-gen pipeline needs
before station fills can land.

## Problem

BSP leaves are atomic buildings; roads are perimeter filler. Connecting
buildings as first-class corridors needs:

1. **Corridor abstraction** — a corridor is a narrow (1–2 cell width)
   connective structure between two separate buildings. Today, road
   frames fill this role implicitly but aren't marked as "corridor"
   vs. "street."
2. **Building-entry coordination** — two adjacent buildings' doorways
   could face across a road frame, creating a natural "corridor" between
   them. But `BuildingShellCore` places doorways independently; no check
   for alignment.
3. **`RoadGraph` doesn't track inter-building edges** — it skeletonizes
   the road mask into nodes + edges with NO concept of "this edge
   connects building A's exit to building B's entrance."

## Sketch of what's needed

- Post-fill pass: walk each compound's / fill's doorways; for each
  doorway facing an adjacent leaf, mark the road cells between as
  "corridor."
- Extend the `BlockFiller` / `CompoundFiller` contract: fillers
  optionally emit a `List<Corridor>` (endpoints, width, purpose).
- `RoadGraphBuilder` tags edges that fall entirely within a single
  corridor.

## Risk / scoping note (carried from the refactor)

Station fills will likely need a **different orchestrator entirely**
(corridors-as-primary, rooms-as-vertices) rather than retrofitting BSP.
When that work lands, `PartitionStrategy` may need to generalize — e.g.
carving sub-rooms inside an arbitrary polygon, not just an axis-aligned
bbox. Keep `PartitionStrategy` and `RoomPurpose` from baking in the
BSP-only "atomic bbox leaf" shape so this story stays open.

**Out of scope for the room-purpose refactor sequence** — captured so the
abstraction designer doesn't accidentally bake the BSP-only shape into
`PartitionStrategy`.

## Cross-refs

- [`../pipeline-audit.md`](../pipeline-audit.md) — the coupling points
  this story has to break (§ Coupling points, § Corridors).
- [`../complete/room-purpose-refactor.md`](../complete/room-purpose-refactor.md)
  — the shipped refactor whose "Risk: corridors-as-first-class" section
  this story formalizes.
- [`station-interior-fills`](station-interior-fills.md) — the consumer
  that depends on this landing first.
