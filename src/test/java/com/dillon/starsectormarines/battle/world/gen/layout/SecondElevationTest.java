package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Elevation is stamped once: a layout Ridge never silently swallows another relief. */
class SecondElevationTest {

    private static final RidgePlanner.Band BAND =
            new RidgePlanner.Band(List.of(new Point(10, 20), new Point(90, 20)), 8, 1, 6);
    private static final Ridge RIDGE = new Ridge("ridge", BAND, false);

    @Test
    void oneRidgeOverAHeldBandIsRaised() {
        Landscape held = Landscape.STOCK.withHeld(List.of(BAND));
        assertNull(MissionLayoutStage.secondElevation(List.of(RIDGE), held, 0));
    }

    @Test
    void aRidgeBesideAnotherReliefIsRefused() {
        Landscape ridged = new Landscape(Landscape.STOCK_WOODLAND, List.of(), List.of(), 0, List.of(BAND));
        assertNotNull(MissionLayoutStage.secondElevation(List.of(RIDGE), ridged, 0));
        assertNotNull(MissionLayoutStage.secondElevation(List.of(RIDGE), Landscape.STOCK, 2));
        assertNotNull(MissionLayoutStage.secondElevation(List.of(RIDGE, new Ridge("other", BAND, false)),
                Landscape.STOCK, 0));
    }
}
