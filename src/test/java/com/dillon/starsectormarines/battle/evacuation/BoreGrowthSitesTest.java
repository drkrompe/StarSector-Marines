package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoreGrowthSitesTest {

    private static final long SEED = 20261025L;

    private static NavigationGrid open() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 40; x++) grid.setWalkable(x, y, true);
        return grid;
    }

    private static SwarmBore bore(int number) {
        return new SwarmBore(number, false, 1L, 20, 20, 0, 1f);
    }

    /** How far {@code site} turns from the middle of the bore's approach, in radians, 0 to pi. */
    private static double offApproach(SwarmBore bore, int[] site) {
        double bearing = Math.atan2(site[1] + 0.5 - (bore.y() + 0.5), site[0] + 0.5 - (bore.x() + 0.5));
        double d = Math.abs(bearing - BoreGrowthSites.approach(bore, SEED)) % (2 * Math.PI);
        return d > Math.PI ? 2 * Math.PI - d : d;
    }

    @Test
    void leavesTheApproachOpen() {
        for (int n = 1; n <= 6; n++) {
            SwarmBore bore = bore(n);
            for (int[] site : BoreGrowthSites.cells(open(), bore, SEED)) {
                // Half the gap, less the slack a site's cell rounding can turn it by.
                assertTrue(offApproach(bore, site) > (2 * Math.PI - BoreGrowthSites.ARC) / 2 - 0.3,
                        "bore " + n + " grew in its approach at " + site[0] + "," + site[1]);
            }
        }
    }

    @Test
    void theBackFillsFirstThenTheHorns() {
        SwarmBore bore = bore(3);
        List<int[]> sites = BoreGrowthSites.cells(open(), bore, SEED);
        assertEquals(BoreGrowthSites.SLOTS, sites.size());
        assertTrue(offApproach(bore, sites.get(0)) > Math.PI - 0.3, "the first site is the back of the den");
        double horn = (2 * Math.PI - BoreGrowthSites.ARC) / 2;
        assertTrue(offApproach(bore, sites.get(1)) < horn + 0.3 && offApproach(bore, sites.get(2)) < horn + 0.3,
                "the next two are the horn tips");
    }

    @Test
    void theSameBoreGrowsTheSameSites() {
        List<int[]> a = BoreGrowthSites.cells(open(), bore(2), SEED), b = BoreGrowthSites.cells(open(), bore(2), SEED);
        for (int i = 0; i < a.size(); i++) assertArrayEquals(a.get(i), b.get(i));
    }
}
