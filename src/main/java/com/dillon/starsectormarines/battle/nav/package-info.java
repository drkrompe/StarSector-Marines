/**
 * Framework core — the navigation substrate.
 *
 * <p>Category: framework core (mechanism; no single feature owner).
 * <br>Charter:  grid model + topology ({@code NavigationGrid}), A*
 *           pathfinding ({@code GridPathfinder}), line-of-sight (+ the
 *           per-tick {@code LosCache}), the cardinal {@code Direction}
 *           helper, the zone graph ({@code zone/}), and the derived greedy
 *           rectangular acceleration mesh ({@code mesh/}) consumed by
 *           coarse-to-fine {@code HierarchicalPathfinder} searches.
 * <br>Boundary: pure spatial mechanism with no actor knowledge. Tactical
 *           "where should I go" decisions belong in {@code decision/},
 *           not here. For an ordinary route use {@code NavigationService};
 *           it searches greedy regions, refines the resulting corridor with
 *           authoritative cell A*, and falls back to unrestricted
 *           {@code GridPathfinder} whenever the corridor is stale,
 *           ineffective, or too indirect. {@code zone/ZoneGraph} honors
 *           shared cardinal transitions while retaining doorway cells as
 *           explicit portals. {@code SharedEdgeBarrier} is the canonical
 *           authored identity for a physical feature that owns one such
 *           transition without consuming either adjacent cell.
 *           {@code mesh/GreedyNavigationMesh} combines compatible cells but
 *           remains a revisioned cache rebuilt from that authoritative grid.
 *           For radius
 *           queries use the {@code unit/} spatial indices, never a raw
 *           grid walk.
 *
 * <p><b>Derivations of the grid are tiled and catch up from its change log.</b>
 * {@code NavigationGrid} counts every write to a cell's flags or an edge's
 * passability in {@code topologyRevision()} and names the cell in a ring log
 * ({@code changeCount()} / {@code changedCellAt(seq)}). Anything derived from
 * the grid over its whole area — the greedy mesh here, the vehicle clearance
 * mask and its component labels in {@code vehicle/} — holds the count it last
 * caught up to and, when the revision moves, recomputes only the tiles (or the
 * radius neighbourhood) holding a logged cell, re-deriving whatever crosses a
 * tile seam. A reader the log no longer reaches back to
 * ({@code hasCaughtUpFrom} false, or a whole-grid {@code clear()}) rebuilds
 * whole; that is the fallback, never the steady state. The rule exists
 * because a one-cell wall breach on a 560x336 map used to cost every one of
 * those derivations a sweep of 188,160 cells on the tick it happened, and the
 * zone graph ({@code zone/ZoneGraph.applyCellsOpened}) was the only one that
 * did not. A new whole-map derivation of the grid follows the same shape.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.nav;
