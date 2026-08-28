/**
 * Framework core — the navigation substrate.
 *
 * <p>Category: framework core (mechanism; no single feature owner).
 * <br>Charter:  grid model + topology ({@code NavigationGrid}), A*
 *           pathfinding ({@code GridPathfinder}), line-of-sight (+ the
 *           per-tick {@code LosCache}), the cardinal {@code Direction}
 *           helper, and the zone graph ({@code zone/}).
 * <br>Boundary: pure spatial mechanism with no actor knowledge. Tactical
 *           "where should I go" decisions belong in {@code decision/},
 *           not here. For "can I walk there" use {@code GridPathfinder}
 *           (it honors edges); {@code zone/ZoneGraph} honors shared cardinal
 *           transitions while retaining doorway cells as explicit portals.
 *           For radius
 *           queries use the {@code unit/} spatial indices, never a raw
 *           grid walk.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.nav;
