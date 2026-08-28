# Continuous Positions

Status: SHIPPED — ground combat uses continuous cell-space positions over a discrete navigation grid.

Written: 2026-08-23

Updated: 2026-08-28 — distinguished cell standability from shared-edge transition passability.

## Vocabulary

- **Continuous position** is a point in cell space. Cell `(cx, cy)` occupies `[cx, cx + 1) × [cy, cy + 1)` and its center is `(cx + 0.5, cy + 0.5)`.
- **Grid projection** is the derived cell containing a point: `floor(x), floor(y)`. It is not a second stored location.
- **Cell center** is the canonical point for a cell-native destination, spawn, or completed path waypoint.
- **Path** is a grid route: an ordered sequence of cell destinations connected
  by passable transitions. It guides a continuous mover but is not the mover's
  location.
- **Arrival** means being within the arrival radius of a named cell center. **Settled** means the current path is exhausted. **Repath permission** is a throttle decision. These are distinct questions.
- **Footprint radius** is a unit type's physical extent in continuous space.
  It applies to picking, blast reach, separation, and physical ballistic
  contacts. Aim may name an entity, but the resolved ray can contact another
  body first.
- **Separation** is the post-movement physical relaxation of overlapping ground footprints. **Formation steering** is a weaker, movement-scoped attempt to preserve useful allied spacing. Neither is a path planner or a hard reservation system.
- **Spatial snapshot** is the tick-local float-position view used to prune nearby-unit queries. Buckets use a point's projected grid cell, while distance decisions use its unprojected position.

## Ownership and flow

`POSITION` is the sole authoritative location for a ground combat entity and remains meaningful for its corpse presentation. Cell-native setup data is converted to a center when an entity enters the world; no render-only or mirrored grid coordinate is maintained.

Navigation retains ownership of discrete map facts. A cell owns whether an
agent may stand in its area, while a shared cardinal edge owns whether an agent
may transition between two standable cells; this permits thin boundary
barriers without declaring either adjacent cell unusable. A* routes,
walkability and occupancy density, perception line of sight, fog, zones, and
topology all consume a grid projection at their boundary. A grid result is
converted back to a center only when it becomes a point-space destination. Direct-fire
legality is the narrow exception: it traces the source and intended target's
true points through those same discrete blocker cells so separation within a
cell cannot invent a firing lane the physical round does not have.

The movement service follows the center-based path continuously, records the velocity actually applied this tick, and pins a completed route exactly to its final center. Appearance derives travel state from applied velocity, so it follows the same movement that simulation used. Post-movement separation may make a bounded, walkability-guarded adjustment; later combat, presentation, and proximity consumers see that final position.

Nearby-unit queries snapshot true positions once per tick. Point-space consumers use those positions and footprints: picking tests the cursor against an expanded unit extent, and explosions test their endpoint against each candidate's blast-expanded footprint. Grid consumers deliberately project through `floor` instead of rounding or retaining a stale cell.

## Laws

1. A ground entity has one position. Rendering, audio, effects, range, proximity, and corpse presentation read that point; a cell is always derived or explicitly authored grid data.
2. The cell-space convention is fixed: cells are half-open unit squares, centers end in `.5`, and the grid projection is `floor`. A new boundary must state which side of that conversion it owns.
3. Grid algorithms remain grid algorithms. Continuous motion does not turn A*,
   perception line of sight, fog, occupancy, zones, or map topology into
   geometric systems. Navigation connectivity requires both a standable
   destination cell and a passable shared transition; zones must not infer
   connectivity from walkability alone. Direct fire may intersect a continuous
   segment with authored blocker shapes; it does not create a second terrain
   model.
4. Arrival, settling, and repath permission must never be inferred from one old-style progress flag or exact point equality. A completed route pins its final center so arrival and settling agree for its own destination.
5. A live spatial distance must use true positions. A bucket, cache key, destination cell, or map lookup may use projected cells only where the discrete abstraction is the intended authority.
6. Radius is the shared interaction footprint. Direct-fire aim remains
   entity-targeted, while ballistic resolution tests the physical ray against
   unit radii and may contact an incidental body first.
7. Separation is soft, deterministic, and subordinate to authored movement: it relaxes overlap over time, never becomes hard collision, stays on walkable space, and cannot move a unit faster than its intent permits. Static ground emplacements anchor; independently kinematic craft do not participate.
8. Formation steering may shape a coherent moving allied group, but it must
   remain weaker than physical separation and must yield in constrained
   terrain. A brief movement-derived heading may finish settling infantry that
   already share one authored destination; it expires and never applies across
   distinct posts. Formation does not grant units a shared destination or
   override individual orders.

## Boundaries and extension points

Air and convoy systems own their own continuous bodies; they are not ground `POSITION` occupants merely because they render in the same battle. Drones that are position-slaved to a flight body likewise stay outside ground separation.

Future work may improve path geometry, tactical scoring precision, or
firing-position selection, but must preserve the projection boundary and the
separate point-authoritative direct-fire boundary.
Surface-to-surface range rules and broader ballistic-contact changes belong to
the combat model; they are not implied by continuous positions.
