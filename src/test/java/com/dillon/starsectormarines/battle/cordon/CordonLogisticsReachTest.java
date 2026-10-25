package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
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
}
