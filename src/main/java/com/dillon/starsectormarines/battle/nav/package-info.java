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
 * <p>See {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.nav;
