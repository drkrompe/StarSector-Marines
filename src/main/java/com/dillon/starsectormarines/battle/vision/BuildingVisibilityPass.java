package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Periodic pass that decides which buildings reveal their interiors to the
 * player. Driven by {@link com.dillon.starsectormarines.battle.sim.BattleSimulation}
 * at roughly 10 Hz (every 3rd 30 Hz tick) — the render path lerps
 * {@code currentAlpha → targetAlpha} per frame so the cadence stutter doesn't
 * pop visibly.
 *
 * <p>Per building, the reveal rule is:
 * <ol>
 *   <li>Any contributor unit (faction in
 *       {@link PlayerVisionState#contributors}) standing inside the building's
 *       bbox → instant reveal. Cheap bbox test, no raycast.</li>
 *   <li>Otherwise, find the closest contributor unit and raycast LOS to a
 *       sparse perimeter sample: bbox corners + bbox center. If any hit, the
 *       building reveals.</li>
 *   <li>No contributor in raycast range or all rays blocked by walls → roof
 *       stays opaque.</li>
 * </ol>
 *
 * <p>The sparse-sample approach trades a fully accurate reveal (would need
 * one ray per perimeter cell) for cheap O(buildings × 1 nearest unit × 5 rays)
 * work that's invisible to the player — a 4×4 room is small enough that
 * "any of 5 sample points visible" is indistinguishable from "any cell
 * visible." For larger buildings the same approximation tends to over-reveal
 * by a tile or two of latency near the perimeter, which reads fine.
 */
public final class BuildingVisibilityPass {

    /** Maximum cell distance from a contributor unit at which a building can be revealed by LOS. */
    private static final int MAX_VISION_RANGE = 18;

    private BuildingVisibilityPass() {}

    public static void update(Buildings buildings,
                              UnitRegistry registry,
                              NavigationGrid grid,
                              PlayerVisionState vision) {
        if (buildings == null || buildings.isEmpty()) return;

        for (Building b : buildings.all()) {
            b.targetAlpha = computeTargetAlpha(b, registry, grid, vision);
        }
    }

    private static float computeTargetAlpha(Building b,
                                            UnitRegistry registry,
                                            NavigationGrid grid,
                                            PlayerVisionState vision) {
        // Pass 1 — anyone inside the bbox? Cheap. (Dense registry is live-only.)
        for (int i = 0, n = registry.liveCount(); i < n; i++) {
            Unit u = registry.get(i);
            if (!vision.isContributor(u.faction)) continue;
            if (b.containsCell(u.getCellX(), u.getCellY())) {
                return 0f;
            }
        }

        // Pass 2 — closest outside contributor + perimeter raycast.
        int cx = (b.minX + b.maxX) >> 1;
        int cy = (b.minY + b.maxY) >> 1;
        Unit closest = null;
        int closestDistSq = MAX_VISION_RANGE * MAX_VISION_RANGE + 1;
        for (int i = 0, n = registry.liveCount(); i < n; i++) {
            Unit u = registry.get(i);
            if (!vision.isContributor(u.faction)) continue;
            int dx = u.getCellX() - cx;
            int dy = u.getCellY() - cy;
            int distSq = dx * dx + dy * dy;
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = u;
            }
        }
        if (closest == null) return 1f;

        // Five perimeter samples — four bbox corners + center. Cheap to LOS
        // and covers a wider arc than just the centroid (a unit peeking around
        // one corner of a long building would otherwise have to walk further
        // to reveal it).
        if (grid.hasLineOfSight(closest.getCellX(), closest.getCellY(), cx, cy)) return 0f;
        if (grid.hasLineOfSight(closest.getCellX(), closest.getCellY(), b.minX, b.minY)) return 0f;
        if (grid.hasLineOfSight(closest.getCellX(), closest.getCellY(), b.maxX, b.minY)) return 0f;
        if (grid.hasLineOfSight(closest.getCellX(), closest.getCellY(), b.minX, b.maxY)) return 0f;
        if (grid.hasLineOfSight(closest.getCellX(), closest.getCellY(), b.maxX, b.maxY)) return 0f;

        return 1f;
    }
}
