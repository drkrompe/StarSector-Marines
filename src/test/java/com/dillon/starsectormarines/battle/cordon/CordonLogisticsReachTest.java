package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Plateau;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Ramp;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What counts as a carrier having reached a redoubt: near it, and on its level. */
class CordonLogisticsReachTest {

    /** A round top about (24, 15), ramped from the west. */
    static final ElevationMap HILL = ElevationLayout.resolve(new ElevationPlan(40, 30, 1, 0, 7,
            List.of(new Plateau(1, 8, List.of(new Point(24, 15)))),
            List.of(new Ramp(new Point(13, 15), new Point(19, 15), 3)))).toMap(ElevationMap.SightRule.SKYLINE);

    @Test void aRedoubtOnATopIsReachedOnTheTopNotUnderItsCliff() {
        int[] redoubt = {24, 15};
        assertTrue(CordonLogistics.at(HILL, 22, 15, redoubt), "on the top beside it");
        assertFalse(CordonLogistics.at(HILL, 35, 15, redoubt), "below the cliff, as near as the reach allows");
    }

    @Test void onFlatGroundNearIsEnough() {
        int[] redoubt = {24, 15};
        assertTrue(CordonLogistics.at(null, 35, 15, redoubt));
        assertFalse(CordonLogistics.at(null, 24 + CordonLogistics.APRON_REACH + 1, 15, redoubt));
    }

    @Test void aShedWithNoMachineDoorIsRefusedButAnOpenAirDepotIsNot() {
        NavigationGrid grid = new NavigationGrid(60, 40);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 60; x++) grid.setWalkableFloor(x, y);
        List<Gantry> berths = List.of(new Gantry(20, 20, 3, 2, Gantry.Facing.SOUTH, Gantry.Holds.MACHINE));
        List<int[]> line = List.of(new int[]{45, 20});
        assertFalse(CordonLogistics.apronReaches(grid, berths, line, true), "a shed with hand doors only");
        assertTrue(CordonLogistics.apronReaches(grid, berths, line, false), "open-air gantries have no door");
        for (int x = 19; x <= 21; x++) grid.setDoorway(x, 15, true);
        assertTrue(CordonLogistics.apronReaches(grid, berths, line, true), "a shed with its machine door");
    }
}
