package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hall frame keeps its hall where its way to the egress does not fit it (`derelict-watch.md`). */
class HallFrameReachTest {

    /** Two 8x8 halls joined by a way {@code join} rows wide; the egress is in the second hall. */
    private static NavigationGrid halls(int join) {
        NavigationGrid grid = new NavigationGrid(30, 12);
        for (int x = 1; x <= 8; x++) for (int y = 2; y <= 9; y++) grid.setWalkableFloor(x, y);
        for (int x = 21; x <= 28; x++) for (int y = 2; y <= 9; y++) grid.setWalkableFloor(x, y);
        for (int x = 9; x <= 20; x++) for (int y = 5; y < 5 + join; y++) grid.setWalkableFloor(x, y);
        return grid;
    }

    private static final List<int[]> EGRESS = List.of(new int[] {25, 6});

    @Test
    void aHallFrameReachesTheEgressThroughAHallJoinButNotACorridor() {
        float hall = DerelictWalkerFrame.HALL.chassis.radius;
        assertTrue(DerelictSecurity.reaches(halls(3), hall, new int[] {4, 6}, EGRESS), "a three-cell join");
        assertFalse(DerelictSecurity.reaches(halls(2), hall, new int[] {4, 6}, EGRESS), "a corridor holds it");
        assertTrue(DerelictSecurity.reaches(halls(2), DerelictWalkerFrame.CHASSIS.radius, new int[] {4, 6}, EGRESS),
                "the heavy passes the corridor");
    }

    @Test
    void withNoEgressThereIsNothingToReach() {
        assertFalse(DerelictSecurity.reaches(halls(3), 0.4f, new int[] {4, 6}, List.of()));
    }
}
