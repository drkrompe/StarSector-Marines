package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CordonStagingPostTest {

    private static final int SIZE = 64, LIFT = 32;
    private static final PickupCordonWorks.Open FIELD = (x, y) -> x >= 0 && y >= 0 && x < SIZE && y < SIZE;

    /** The lane out of the east gate, lift to the board's edge. */
    private static List<int[]> eastLane() {
        List<int[]> lane = new ArrayList<>();
        for (int x = LIFT; x < SIZE; x++) lane.add(new int[]{x, LIFT});
        return lane;
    }

    /** The five landing points thirteen out, the first due north, as the placement lays them. */
    private static int[] formation() {
        int[] cells = new int[10];
        for (int p = 0; p < 5; p++) {
            double angle = Math.PI / 2 + p * Math.PI * 2 / 5;
            cells[p * 2] = (int) Math.round(LIFT + Math.cos(angle) * 13);
            cells[p * 2 + 1] = (int) Math.round(LIFT + Math.sin(angle) * 13);
        }
        return cells;
    }

    @Test
    void theStationsStandBehindTheLineOffEveryWayInWithTheDumpOnTheCompanysSide() {
        // The threat due north; the company's pad due east.
        List<CordonStagingPost.Placed> post = CordonStagingPost.plan(LIFT, LIFT, FIELD, List.of(eastLane()),
                formation(), LIFT, SIZE, SIZE - 1, LIFT, true);
        assertEquals(3, post.size(), "all three stations fit an open field");
        assertEquals(CordonStagingPost.HQ.name(), post.get(0).station().name());
        Set<Long> pieces = new HashSet<>();
        for (CordonStagingPost.Placed s : post) {
            assertTrue(s.anchorY() < LIFT, s.station().name() + " not behind the line, at " + s.anchorY());
            for (CordonStagingPost.Piece p : s.station().pieces()) {
                for (int y = s.anchorY() + p.dy(); y < s.anchorY() + p.dy() + p.height(); y++) {
                    for (int x = s.anchorX() + p.dx(); x < s.anchorX() + p.dx() + p.width(); x++) {
                        assertTrue(pieces.add(PickupCordonWorks.key(x, y)), "two pieces share " + x + "," + y);
                        assertTrue(PickupCordonWorks.ring(x - LIFT, y - LIFT) <= CordonStagingPost.INNERMOST_TRENCH_WALK,
                                "a piece on the trench's walkway at " + x + "," + y);
                        assertFalse(Math.abs(x - LIFT) <= CordonStagingPost.CRAFT_HALF_WIDTH
                                && Math.abs(y - LIFT) <= CordonStagingPost.CRAFT_HALF_LENGTH, "a piece under the craft");
                        assertTrue(Math.abs(y - LIFT) > CordonStagingPost.LANE_CLEARANCE || x < LIFT,
                                "a piece on the lane in at " + x + "," + y);
                    }
                }
            }
        }
        CordonStagingPost.Placed dump = post.stream().filter(s -> s.station().name().equals(CordonStagingPost.DUMP.name())).findFirst()
                .orElseThrow();
        assertTrue(dump.anchorX() > LIFT, "the dump on the side the company comes in by");
        assertNotNull(dump.cache());
        assertFalse(pieces.contains(PickupCordonWorks.key(dump.cache()[0], dump.cache()[1])), "the cache on open ground");
        for (CordonStagingPost.Placed s : post) if (s != dump) assertNull(s.cache());
    }

    @Test
    void aStationWithNowhereToStandIsLeftOut() {
        // Only a strip south of the lift is open ground.
        PickupCordonWorks.Open strip = (x, y) -> FIELD.at(x, y) && y < LIFT - 7 && y > LIFT - 11 && Math.abs(x - LIFT) < 4;
        List<CordonStagingPost.Placed> post = CordonStagingPost.plan(LIFT, LIFT, strip, List.of(), formation(),
                LIFT, SIZE, SIZE - 1, LIFT, true);
        assertTrue(post.size() < 3);
    }
}
