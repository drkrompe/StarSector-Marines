package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a convoy's road is carried on into the town's truck yard, and the truck's way round it and out. */
class CordonGateYardTest {

    /**
     * A yard behind a gate on the west wall at (20, 10), running east: nine deep (x 21..29), thirteen along the wall
     * (y 4..16), the staging ground on the high-y hand, so a truck stops at (25, 15).
     */
    private static CordonGateYard yard() {
        List<int[]> cells = new ArrayList<>();
        for (int x = 21; x <= 29; x++) for (int y = 4; y <= 16; y++) cells.add(new int[]{x, y});
        return new CordonGateYard(cells, List.of(), new int[]{20, 10}, new int[]{21, 10}, new int[]{25, 10},
                new int[]{25, 15});
    }

    @Test
    void aTruckDrivesInRunsToTheFarSideSwingsAcrossAndComesBackTowardTheGateToItsStop() {
        CordonGateYard yard = yard();
        List<int[]> loop = yard.loop();
        assertArrayEquals(new int[]{20, 10}, loop.get(0));
        assertArrayEquals(new int[]{25, 15}, loop.get(loop.size() - 1));
        for (int i = 1; i < loop.size(); i++) {
            assertEquals(1, Math.abs(loop.get(i)[0] - loop.get(i - 1)[0]) + Math.abs(loop.get(i)[1] - loop.get(i - 1)[1]),
                    "a step at a time");
            assertTrue(yard.contains(loop.get(i)[0], loop.get(i)[1]), "inside the yard once through the gate");
        }
        int deepest = 0;
        for (int[] c : loop) deepest = Math.max(deepest, c[0]);
        assertEquals(28, deepest, "to the far side before it turns");
        // It arrives moving back toward the gate: it stands to load facing out.
        int[] before = loop.get(loop.size() - 2);
        assertEquals(26, before[0]);
        List<int[]> out = yard.wayOut();
        assertArrayEquals(new int[]{25, 15}, out.get(0));
        assertArrayEquals(new int[]{20 - CordonGateYard.APPROACH, 10}, out.get(out.size() - 1), "forward out of the gate");
        for (int i = 1; i < out.size(); i++) assertTrue(out.get(i)[0] <= out.get(i - 1)[0], "never back into the yard");
    }

    @Test
    void theRoadIsCutWhereItPassesTheGateAndCarriedInThroughIt() {
        // A road along the row of the gate's axis running east past the line-up point and on into the compound.
        List<int[]> road = new ArrayList<>();
        for (int x = 0; x <= 24; x++) road.add(new int[]{x, 10});
        CordonGateYard.Extended extended = yard().extend(road, (x, y) -> true);
        assertNotNull(extended);
        assertArrayEquals(new int[]{20, 10}, extended.cells().get(extended.gateIndex()));
        assertEquals(20, extended.gateIndex(), "cut at the line-up point and in on the axis");
        assertArrayEquals(new int[]{25, 15}, extended.cells().get(extended.cells().size() - 1));
    }

    @Test
    void aRoadEndingShortIsSearchedOnToTheGateAndNoneWhereNoTruckCanReachIt() {
        List<int[]> road = new ArrayList<>();
        for (int y = 30; y >= 22; y--) road.add(new int[]{8, y});
        CordonGateYard.Extended extended = yard().extend(road, (x, y) -> true);
        assertNotNull(extended);
        assertEquals(road.size() - 1 + 8 + 12 + 4, extended.gateIndex(), "on from the road's end to the line-up point, then in");
        assertNull(yard().extend(road, (x, y) -> y >= 22));
    }
}
