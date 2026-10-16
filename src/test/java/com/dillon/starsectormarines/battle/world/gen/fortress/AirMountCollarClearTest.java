package com.dillon.starsectormarines.battle.world.gen.fortress;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A missile mount's body and the concrete collar about it (reach two from its middle) must stand on the bastion's floor:
 * a collar clipped by the works' own wall loses its pieces, so a mount sited beside the wall reads as broken. The shield
 * fort's pockets are squeezed between its wall and its control room and keep their clipped collars, so only the battery
 * fort is held to this.
 */
class AirMountCollarClearTest {

    @Test
    void noMountOrTowerCollarOverlapsTheWall() {
        for (FortPlan plan : new FortPlan[]{BatteryFort.PLAN}) {
            List<int[]> sites = new ArrayList<>(plan.airBatteries());
            sites.addAll(plan.airTowers());
            assertFalse(sites.isEmpty());
            for (int[] site : sites) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        int x = site[0] + dx, y = site[1] + dy;
                        assertFalse(plan.isWall(x, y),
                                plan.getClass().getName() + " site (" + site[0] + "," + site[1] + ") collar cell ("
                                        + x + "," + y + ") is wall");
                    }
                }
            }
        }
    }
}
