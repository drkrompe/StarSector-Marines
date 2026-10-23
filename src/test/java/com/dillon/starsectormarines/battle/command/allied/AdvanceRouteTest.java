package com.dillon.starsectormarines.battle.command.allied;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvanceRouteTest {

    @Test
    void boundsSplitTheRouteEvenlyAndEndOnTheRallyPoint() {
        AdvanceRoute route = AdvanceRoute.straight(0, 0, 70, 0);
        float[] bounds = route.bounds(24f);
        assertEquals(3, bounds.length);
        assertEquals(70f, bounds[2], 1e-4f);
        assertEquals(bounds[1] - bounds[0], bounds[2] - bounds[1], 1e-4f);
    }

    @Test
    void alongMeasuresDistanceRoundACornerAndRunsOnPastTheEnds() {
        AdvanceRoute route = AdvanceRoute.through(List.of(new int[]{0, 0}, new int[]{20, 0}, new int[]{20, 20}));
        assertEquals(10f, route.along(10.5f, 3f), 1e-4f);
        assertEquals(30f, route.along(25f, 10.5f), 1e-4f);
        assertTrue(route.along(-5f, 0.5f) < 0f, "short of the start reads negative");
        assertTrue(route.along(20.5f, 30f) > route.length(), "past the rally reads beyond it");
    }

    @Test
    void slotsStandAbreastAcrossTheAdvanceCentredOnIt() {
        AdvanceRoute route = AdvanceRoute.straight(0, 10, 40, 10);
        assertArrayEquals(new int[]{20, 4}, route.slot(20f, 0, 3, 6f));
        assertArrayEquals(new int[]{20, 10}, route.slot(20f, 1, 3, 6f));
        assertArrayEquals(new int[]{20, 16}, route.slot(20f, 2, 3, 6f));
    }
}
