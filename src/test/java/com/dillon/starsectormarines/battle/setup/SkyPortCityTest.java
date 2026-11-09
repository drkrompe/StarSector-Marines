package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.model.VoidBackdrop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sky port's city: towers on a street grid that keep a street's width from the port and from each other. */
class SkyPortCityTest {

    @Test
    void aPlotIsTrimmedOffTheSideThatKeepsMostOfItAndDroppedWhenTooLittleIsLeft() {
        assertArrayEquals(new int[]{0, 0, 35, 35}, SkyPortClusterSetup.clear(new int[]{0, 0, 35, 35}, new int[]{50, 0, 60, 35}));
        assertArrayEquals(new int[]{0, 0, 29, 35}, SkyPortClusterSetup.clear(new int[]{0, 0, 35, 35}, new int[]{30, -10, 60, 50}));
        assertNull(SkyPortClusterSetup.clear(new int[]{0, 0, 35, 35}, new int[]{5, 5, 40, 40}));
    }

    @Test
    void noCityTowerComesWithinAStreetOfThePortOrMeetsAnother() {
        List<VoidBackdrop.Tower> city = SkyPortClusterSetup.city(1f);
        assertFalse(city.isEmpty());
        int[][] port = {SkyPortClusterSetup.A, SkyPortClusterSetup.B, SkyPortClusterSetup.C, SkyPortClusterSetup.OUTPOST};
        for (VoidBackdrop.Tower t : city) {
            for (int[] roof : port) {
                boolean apart = t.x1() - 1 < roof[0] - SkyPortClusterSetup.STREET || t.x0() > roof[2] + SkyPortClusterSetup.STREET
                        || t.y1() - 1 < roof[1] - SkyPortClusterSetup.STREET || t.y0() > roof[3] + SkyPortClusterSetup.STREET;
                assertTrue(apart, t + " within a street of a port roof");
            }
            for (VoidBackdrop.Tower o : city) {
                if (o == t) continue;
                assertTrue(t.x1() <= o.x0() || o.x1() <= t.x0() || t.y1() <= o.y0() || o.y1() <= t.y0(), t + " meets " + o);
            }
        }
    }
}
