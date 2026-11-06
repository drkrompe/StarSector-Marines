package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.creep.CreepVegetation;

/**
 * Where a standing tree stops a round: its trunk and dense core, not every cell its canopy touches (owner: a tree
 * taking the hit of fire at its edge felt wrong). A crown's cells close to movement and sight wherever a lobe reaches,
 * branch tips included, so the whole footprint square is solid; a round crossing a crown cell is stopped there only
 * when its path through that cell comes within the core, an ellipse about the footprint's centre spanning
 * {@link #CORE_SHARE} of the footprint on each axis and never narrower than the trunk. A round that only grazes the
 * outer crown flies on through the leaves.
 *
 * <p>Pure geometry in grid cells, so a firing decision ({@code NavigationGrid.hasLineOfFire}) and the round itself
 * ({@code NavigationGrid.firstWallOnLine}) agree about which lines a tree stops.
 */
public final class CanopyCore {
    /** Share of a footprint's width and height the stopping core spans: the trunk and the crown's dense middle. */
    public static final float CORE_SHARE = 0.45f;

    private CanopyCore() {}

    /** The core's radius across a footprint {@code cells} wide on one axis. */
    public static float radius(int cells) {
        return Math.max(CreepVegetation.TRUNK_RADIUS, cells * CORE_SHARE * 0.5f);
    }

    /**
     * Whether the segment ({@code x0},{@code y0})-({@code x1},{@code y1}), where it lies inside cell
     * ({@code cellX},{@code cellY}), comes within the core ellipse centred on ({@code cx},{@code cy}) with radii
     * {@code rx} and {@code ry}. False when the segment does not cross the cell at all.
     */
    public static boolean stops(int cellX, int cellY, float x0, float y0, float x1, float y1,
                                float cx, float cy, float rx, float ry) {
        float dx = x1 - x0, dy = y1 - y0;
        // Clip the segment to the cell (Liang-Barsky), then ask how near the clipped piece passes the centre.
        float t0 = 0f, t1 = 1f;
        for (int edge = 0; edge < 4; edge++) {
            float p = switch (edge) { case 0 -> -dx; case 1 -> dx; case 2 -> -dy; default -> dy; };
            float q = switch (edge) {
                case 0 -> x0 - cellX; case 1 -> cellX + 1 - x0; case 2 -> y0 - cellY; default -> cellY + 1 - y0;
            };
            if (p == 0f) {
                if (q < 0f) return false;
                continue;
            }
            float r = q / p;
            if (p < 0f) t0 = Math.max(t0, r); else t1 = Math.min(t1, r);
            if (t0 > t1) return false;
        }
        // In the ellipse's own frame the core is the unit circle.
        float ax = (x0 + dx * t0 - cx) / rx, ay = (y0 + dy * t0 - cy) / ry;
        float bx = (x0 + dx * t1 - cx) / rx, by = (y0 + dy * t1 - cy) / ry;
        float ex = bx - ax, ey = by - ay;
        float lengthSq = ex * ex + ey * ey;
        float t = lengthSq <= 0f ? 0f : Math.max(0f, Math.min(1f, -(ax * ex + ay * ey) / lengthSq));
        float px = ax + ex * t, py = ay + ey * t;
        return px * px + py * py <= 1f;
    }
}
