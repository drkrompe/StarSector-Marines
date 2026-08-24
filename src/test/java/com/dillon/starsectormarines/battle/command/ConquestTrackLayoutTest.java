package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConquestTrackLayoutTest {

    @Test
    void nonDivisibleLateralExtentHasConsistentSeams() {
        ConquestTrackLayout layout = new ConquestTrackLayout(
                TraversalAxis.SOUTH_TO_NORTH, 160, 240);

        assertEquals(0, layout.lateralStartInclusive(0));
        assertEquals(53, layout.lateralEndInclusive(0));
        assertEquals(54, layout.lateralStartInclusive(1));
        assertEquals(106, layout.lateralEndInclusive(1));
        assertEquals(107, layout.lateralStartInclusive(2));
        assertEquals(159, layout.lateralEndInclusive(2));
        assertEquals(0, layout.trackForCell(53, 10));
        assertEquals(1, layout.trackForCell(54, 10));
        assertEquals(1, layout.trackForCell(106, 10));
        assertEquals(2, layout.trackForCell(107, 10));
    }

    @Test
    void axesShareCanonicalBeachToKeepProgress() {
        ConquestTrackLayout north = new ConquestTrackLayout(
                TraversalAxis.SOUTH_TO_NORTH, 240, 160);
        ConquestTrackLayout east = new ConquestTrackLayout(
                TraversalAxis.WEST_TO_EAST, 240, 160);

        assertEquals(0f, north.assaultProgress(80, 0), 0.0001f);
        assertEquals(1f, north.assaultProgress(80, 159), 0.0001f);
        assertEquals(0f, east.assaultProgress(0, 80), 0.0001f);
        assertEquals(1f, east.assaultProgress(239, 80), 0.0001f);
    }
}
