package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Elevation is stamped once: ridges go up together, and a layout Ridge never silently swallows plateaus. */
class SecondElevationTest {

    private static final RidgePlanner.Band BAND =
            new RidgePlanner.Band(List.of(new Point(10, 20), new Point(90, 20)), 8, 1, 6);
    private static final Ridge RIDGE = new Ridge("ridge", BAND, false);

    @Test
    void ridgesAreRaisedTogether() {
        assertNull(MissionLayoutStage.secondElevation(List.of(RIDGE, new Ridge("other", BAND, false)), 0));
    }

    @Test
    void aRidgeBesidePlateausIsRefused() {
        assertNotNull(MissionLayoutStage.secondElevation(List.of(RIDGE), 2));
    }
}
