package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsBatteryGunsTest {

    /** An interior on the map's east half, two aprons on its west face, the landing far west. */
    private static final RuinsDelve.Area INTERIOR = new RuinsDelve.Area(60, 10, 100, 70);
    private static final List<RuinsDelve.Area> APRONS =
            List.of(new RuinsDelve.Area(50, 20, 59, 28), new RuinsDelve.Area(50, 50, 59, 58));
    private static final RuinsBatteryGuns.Open OPEN = (x, y) -> x >= 0 && y >= 0 && x < 120 && y < 80;

    @Test
    void batteriesStandOnTheApproachOutsideTheApronsAndTheInterior() {
        List<int[]> sites = RuinsBatteryGuns.sites(APRONS, INTERIOR, 5, 40, 4, OPEN);
        assertEquals(4, sites.size());
        for (int[] site : sites) {
            assertFalse(INTERIOR.contains(site[0], site[1]));
            for (RuinsDelve.Area apron : APRONS) assertFalse(apron.contains(site[0], site[1]));
            assertTrue(site[0] < INTERIOR.left(), "a battery faces the landing: " + site[0]);
        }
    }

    @Test
    void batteriesStandApart() {
        List<int[]> sites = RuinsBatteryGuns.sites(APRONS, INTERIOR, 5, 40, 4, OPEN);
        for (int i = 0; i < sites.size(); i++) {
            for (int j = i + 1; j < sites.size(); j++) {
                int dx = sites.get(i)[0] - sites.get(j)[0];
                int dy = sites.get(i)[1] - sites.get(j)[1];
                assertTrue(dx * dx + dy * dy >= RuinsBatteryGuns.SPACING * RuinsBatteryGuns.SPACING);
            }
        }
    }

    @Test
    void batteriesStandOnlyOnOpenGround() {
        RuinsBatteryGuns.Open westBlocked = (x, y) -> OPEN.at(x, y) && x > 45;
        for (int[] site : RuinsBatteryGuns.sites(APRONS, INTERIOR, 5, 40, 2, westBlocked)) {
            assertTrue(westBlocked.at(site[0], site[1]));
        }
        assertTrue(RuinsBatteryGuns.sites(APRONS, INTERIOR, 5, 40, 3, (x, y) -> false).isEmpty());
    }

    @Test
    void noApronsOrNoCountStandsNone() {
        assertTrue(RuinsBatteryGuns.sites(List.of(), INTERIOR, 5, 40, 3, OPEN).isEmpty());
        assertTrue(RuinsBatteryGuns.sites(APRONS, INTERIOR, 5, 40, 0, OPEN).isEmpty());
    }
}
