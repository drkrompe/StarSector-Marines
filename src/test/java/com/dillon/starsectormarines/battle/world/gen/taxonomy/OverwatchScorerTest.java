package com.dillon.starsectormarines.battle.world.gen.taxonomy;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariants for {@link OverwatchScorer} — the positional (corner-tower) read
 * promoted out of the preview gut-check. Driven through the real
 * {@link BspCityGenerator} over the standard seed batch so the scorer is
 * validated against the maps it will actually place turrets on.
 *
 * <p>Each returned site must be a genuine corner-tower: a walkable cell with an
 * in-bounds wall at its back and a real outward field of fire. In conquest mode
 * every site must fire <em>toward the attacker</em>; in legacy mode any cardinal
 * is allowed. The set must be spatially de-duplicated and reproducible.
 */
public class OverwatchScorerTest {

    private static final long[] LEGACY_SEEDS = { 1L, 42L, 100L, 777L, 1234L, 9999L };
    private static final long[] CONQUEST_SEEDS = { 1L, 42L, 100L, 777L };

    @Test
    void everySiteIsAGenuineCornerTower() {
        BspCityGenerator gen = new BspCityGenerator();
        for (long seed : CONQUEST_SEEDS) {
            MapResult map = gen.generate(240, 160, seed, TraversalAxis.SOUTH_TO_NORTH);
            assertSitesValid(map.grid, sites(gen, map, TraversalAxis.SOUTH_TO_NORTH), seed);
        }
        for (long seed : LEGACY_SEEDS) {
            MapResult map = gen.generate(80, 80, seed);
            assertSitesValid(map.grid, sites(gen, map, null), seed);
        }
    }

    private static List<OverwatchSite> sites(BspCityGenerator gen, MapResult map, TraversalAxis axis) {
        return OverwatchScorer.findSites(map.grid, gen.getLastTacticalRegions(), axis);
    }

    private static void assertSitesValid(NavigationGrid grid, List<OverwatchSite> sites, long seed) {
        int w = grid.getWidth(), h = grid.getHeight();
        for (OverwatchSite s : sites) {
            assertTrue(grid.isWalkable(s.x(), s.y()),
                    () -> "seed " + seed + ": site not walkable at " + s.x() + "," + s.y());
            // Firing direction is exactly one cardinal.
            assertEquals(1, Math.abs(s.dirX()) + Math.abs(s.dirY()),
                    () -> "seed " + seed + ": non-cardinal firing dir " + s);
            // Cover at back: opposite the firing dir is an in-bounds wall (not OOB).
            int bx = s.x() - s.dirX(), by = s.y() - s.dirY();
            assertTrue(bx >= 0 && bx < w && by >= 0 && by < h,
                    () -> "seed " + seed + ": back cell OOB (edge-mounted) for " + s);
            assertFalse(grid.isWalkable(bx, by),
                    () -> "seed " + seed + ": no wall at back of " + s);
            // Real field of fire: at least MIN_REACH walkable cells ahead before a wall.
            assertTrue(outwardRun(grid, s) >= OverwatchScorer.MIN_REACH,
                    () -> "seed " + seed + ": outward run below MIN_REACH for " + s);
        }
        // Non-max suppression: no two kept sites within SEPARATION (Manhattan).
        for (int i = 0; i < sites.size(); i++) {
            for (int j = i + 1; j < sites.size(); j++) {
                OverwatchSite a = sites.get(i), b = sites.get(j);
                int man = Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
                assertTrue(man >= OverwatchScorer.SEPARATION,
                        () -> "seed " + seed + ": sites within suppression radius " + a + " / " + b);
            }
        }
    }

    /** Walkable cells ahead of the site in its firing direction before a wall (excluding the site). */
    private static int outwardRun(NavigationGrid grid, OverwatchSite s) {
        int run = 0;
        int x = s.x() + s.dirX(), y = s.y() + s.dirY();
        while (x >= 0 && x < grid.getWidth() && y >= 0 && y < grid.getHeight() && grid.isWalkable(x, y)) {
            run++;
            x += s.dirX();
            y += s.dirY();
        }
        return run;
    }

    @Test
    void conquestSitesFireTowardTheAttacker() {
        BspCityGenerator gen = new BspCityGenerator();
        // SOUTH_TO_NORTH: attacker enters from y=0, so a defender overwatch must
        // fire south (dirY == -1, dirX == 0).
        for (long seed : CONQUEST_SEEDS) {
            MapResult map = gen.generate(240, 160, seed, TraversalAxis.SOUTH_TO_NORTH);
            List<OverwatchSite> sites = sites(gen, map, TraversalAxis.SOUTH_TO_NORTH);
            assertFalse(sites.isEmpty(), "conquest seed " + seed + " produced no overwatch sites");
            for (OverwatchSite s : sites) {
                assertEquals(0, s.dirX(), () -> "conquest site fires off-axis " + s);
                assertEquals(-1, s.dirY(), () -> "conquest site not firing attacker-ward " + s);
            }
        }
    }

    @Test
    void deterministicFromSeed() {
        BspCityGenerator gen = new BspCityGenerator();
        MapResult a = gen.generate(240, 160, 777L, TraversalAxis.SOUTH_TO_NORTH);
        List<OverwatchSite> sitesA = sites(gen, a, TraversalAxis.SOUTH_TO_NORTH);
        MapResult b = gen.generate(240, 160, 777L, TraversalAxis.SOUTH_TO_NORTH);
        List<OverwatchSite> sitesB = sites(gen, b, TraversalAxis.SOUTH_TO_NORTH);
        assertEquals(sitesA, sitesB, "overwatch sites not reproducible from seed");
    }
}
