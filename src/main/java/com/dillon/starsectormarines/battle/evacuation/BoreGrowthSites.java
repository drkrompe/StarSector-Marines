package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a bore grows things around itself: a broken crescent of sites outside
 * its mouth (`colony-breach-nouns.md`, Spore colony). Spore colonies take these
 * today; a later ground-attacking growth reuses the same placement, so it is
 * its own seam rather than a detail of the umbrella.
 *
 * <p>The crescent sits beyond the mouth the surges pour out of, so a growth never
 * stands where a body is released, and it wraps only part of the way round:
 * the rest is the den's open approach, turned by a seeded per-bore angle so
 * neighbouring bores do not open the same way. Each site stands at its own
 * seeded distance, so the colonies are not a drawn ring. The back of the den
 * fills first, then the two horns, then the gaps between, so a bore's first
 * three colonies already read as a crescent. A site on closed ground is skipped.
 * Pure over the grid: the same bore on the same grid gives the same sites in
 * the same order.
 */
public final class BoreGrowthSites {

    /** Cells from the bore to its nearest growth site. */
    public static final float RADIUS = SwarmBores.MOUTH_RADIUS + 1.5f;
    /** Extra cells a site may stand beyond {@link #RADIUS}, seeded per site. */
    public static final float RADIUS_SPREAD = 2f;
    /** The arc the crescent spans, in radians; the rest of the circle is the open approach. */
    public static final double ARC = Math.toRadians(200);
    /** Sites along the crescent. */
    public static final int SLOTS = 8;

    /** Which open cells a kind of growth may stand on (a ground colony, say, only on creep). */
    @FunctionalInterface
    public interface SiteFilter {
        boolean accepts(int x, int y);
    }

    private BoreGrowthSites() {}

    /** The open growth sites around {@code bore}, in the order growths take them. */
    public static List<int[]> cells(NavigationGrid grid, SwarmBore bore, long seed) {
        return cells(grid, bore, seed, (x, y) -> true);
    }

    /** The bearing, in radians, of the middle of {@code bore}'s open approach. */
    public static double approach(SwarmBore bore, long seed) {
        return SwarmBores.unit(SwarmBores.mix(seed ^ (bore.number() * 0xD6E8FEB86659FD93L))) * Math.PI * 2.0;
    }

    /** The open growth sites around {@code bore} that {@code filter} accepts, in the order growths take them. */
    public static List<int[]> cells(NavigationGrid grid, SwarmBore bore, long seed, SiteFilter filter) {
        double back = approach(bore, seed) + Math.PI;
        List<int[]> cells = new ArrayList<>();
        // Back of the den, the two horns, then the gaps between (slot 0 and 7 are the horn tips).
        int[] order = {4, 0, 7, 2, 5, 3, 6, 1};
        for (int slot : order) {
            double angle = back - ARC / 2 + slot * ARC / (SLOTS - 1);
            float r = RADIUS + RADIUS_SPREAD
                    * SwarmBores.unit(SwarmBores.mix(seed ^ (bore.number() * 0x9E3779B97F4A7C15L) ^ (slot + 1) * 0xC2B2AE3D27D4EB4FL));
            int x = (int) Math.floor(bore.x() + 0.5 + Math.cos(angle) * r);
            int y = (int) Math.floor(bore.y() + 0.5 + Math.sin(angle) * r);
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || !filter.accepts(x, y)) continue;
            boolean duplicate = false;
            for (int[] c : cells) duplicate |= c[0] == x && c[1] == y;
            if (!duplicate) cells.add(new int[]{x, y});
        }
        return cells;
    }
}
