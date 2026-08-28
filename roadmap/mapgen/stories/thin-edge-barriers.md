# Thin edge barriers

Status: IN PROGRESS

Written: 2026-08-28

## Outcome

Maps may author a narrow, grid-aligned barrier on the boundary between two
walkable cells. The barrier consumes neither cell, but units cannot cross it;
sight, direct fire, cover, rendering, and destruction all agree on the same
physical feature.

This story does not add arbitrary within-cell or angled obstacles. A shape that
splits one cell into multiple disconnected interiors requires subcell regions or
a finer low-level navigation lattice and remains a separate architectural
decision.

## Standing constraints

- Cells own standability; shared cardinal edges own transitions.
- The shared edge is one relationship. Authoring and mutation must update both
  cell-local passability halves atomically.
- Diagonal movement may not cut around a blocked edge endpoint.
- Zone and command connectivity honor the same transitions as cell A*.
- One authored barrier identity supplies navigation, ballistic, visibility,
  cover, durability, and presentation semantics. A navigation-only invisible
  wall is not production content.
- Runtime destruction may open an edge and invalidate topology caches. Runtime
  construction or closure is excluded because it can invalidate active routes
  and place a barrier through an occupant.

## Current state

The navigation foundation is present: shared-edge mutation is reciprocal, A*
and diagonal constraints honor the closure, zone flooding and doorway portals
respect it, and runtime opening rebuilds topology and retained path fields at
the ordinary end-of-tick boundary. The derived greedy navigation mesh also
preserves closed edges as region seams and publishes one immutable replacement
snapshot at that same boundary after cell, wreck, or barrier mutation. No
generator publishes a production barrier yet, so the shipped maps remain
unchanged.

## Remaining scope

1. Define the authored thin-barrier record and validation, including canonical
   edge addressing, appearance, opacity/projectile behavior, cover profile, and
   durability.
2. Add one bounded map-generation consumer whose circulation checks prove both
   sides remain usable and the intended route is still connected.
3. Extend exact direct-fire and visibility tracing to intersect the barrier
   segment from the same authored record.
4. Project directional cover to the adjacent stand positions without also
   treating the barrier as a cell-sized cover shape.
5. Render the barrier in the ordinary battle pipeline and add deterministic
   visual evidence.
6. Route destruction through the map editor so the feature disappears,
   opens its shared edge, refreshes cover, and invalidates topology exactly
   once.

## Acceptance

- Two walkable cells separated by a barrier cannot exchange a unit, and a
  legal alternate route remains usable.
- A barrier endpoint cannot be bypassed by one diagonal step.
- Zone, command, and A* reachability agree before and after the barrier opens.
- Shots and sight stop or pass according to the authored barrier profile at
  the exact crossed segment.
- Cover direction and catch height match the physical barrier.
- Destroying the barrier opens the transition without invalidating an existing
  legal detour path.
- The first production consumer has deterministic navigation and rendered
  evidence, with no new global navigation-grid resolution.
