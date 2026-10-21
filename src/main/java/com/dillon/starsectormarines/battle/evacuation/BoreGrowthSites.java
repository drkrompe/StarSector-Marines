package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a bore grows things around itself: a ring of sites just outside its
 * mouth (`colony-breach-nouns.md`, Spore colony). Spore colonies take these
 * today; a later ground-attacking growth reuses the same placement, so it is
 * its own seam rather than a detail of the umbrella.
 *
 * <p>The ring sits beyond the mouth the surges pour out of, so a growth never
 * stands where a body is released. The sites are evenly spaced around the
 * bore, turned by a seeded per-bore angle so neighbouring bores do not grow the
 * same pattern; a site on closed ground is skipped. Pure over the grid: the
 * same bore on the same grid gives the same sites in the same order.
 */
public final class BoreGrowthSites {

    /** Cells from the bore to its growth ring. */
    public static final float RADIUS = SwarmBores.MOUTH_RADIUS + 1.5f;
    /** Sites around the ring. */
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

    /** The open growth sites around {@code bore} that {@code filter} accepts, in the order growths take them. */
    public static List<int[]> cells(NavigationGrid grid, SwarmBore bore, long seed, SiteFilter filter) {
        double turn = SwarmBores.unit(SwarmBores.mix(seed ^ (bore.number() * 0xD6E8FEB86659FD93L))) * Math.PI * 2.0;
        List<int[]> cells = new ArrayList<>();
        // Alternate across the ring (0, 4, 2, 6, 1, 5, 3, 7) so the first few growths stand apart.
        int[] order = {0, 4, 2, 6, 1, 5, 3, 7};
        for (int slot : order) {
            double angle = turn + slot * Math.PI * 2.0 / SLOTS;
            int x = (int) Math.floor(bore.x() + 0.5 + Math.cos(angle) * RADIUS);
            int y = (int) Math.floor(bore.y() + 0.5 + Math.sin(angle) * RADIUS);
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || !filter.accepts(x, y)) continue;
            boolean duplicate = false;
            for (int[] c : cells) duplicate |= c[0] == x && c[1] == y;
            if (!duplicate) cells.add(new int[]{x, y});
        }
        return cells;
    }
}
