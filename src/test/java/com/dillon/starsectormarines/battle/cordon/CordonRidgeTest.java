package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ridge the Cordon's line stands on, as {@link CordonPlan} states it from the redoubts' ground. */
class CordonRidgeTest {

    private static final int WIDTH = 560;
    /** Four redoubts' claimed ground, {x0, y0, x1, y1}, at different depths. */
    private static final List<int[]> REDOUBTS = List.of(
            new int[]{50, 120, 80, 150}, new int[]{190, 126, 220, 156},
            new int[]{330, 118, 360, 148}, new int[]{470, 124, 500, 154});

    @Test
    void runsEdgeToEdgeAlongThePicketCoursesAShadeToTheFront() {
        int[] front = {0, 1};
        RidgePlanner.Band band = CordonPlan.ridgeBand(REDOUBTS, front, WIDTH);
        List<ElevationPlan.Point> path = band.path();
        assertEquals(2 + 2 * REDOUBTS.size(), path.size());
        assertEquals(CordonPlan.RIDGE_EDGE, path.get(0).x());
        assertEquals(WIDTH - 1 - CordonPlan.RIDGE_EDGE, path.get(path.size() - 1).x());
        for (int i = 0; i < REDOUBTS.size(); i++) {
            int[] r = REDOUBTS.get(i);
            double y = (r[1] + r[3]) / 2 + CordonPlan.RIDGE_FORWARD;
            // A picket course leaves a redoubt one cell past its edge at its middle depth: the ridge runs there.
            assertEquals(r[0] - 1, path.get(1 + 2 * i).x());
            assertEquals(r[2] + 1, path.get(2 + 2 * i).x());
            assertEquals(y, path.get(1 + 2 * i).y());
            assertEquals(y, path.get(2 + 2 * i).y());
        }
        for (int i = 1; i < path.size(); i++) assertTrue(path.get(i).x() > path.get(i - 1).x(), "west to east");
    }

    @Test
    void isClimbedFromTheRearWhicheverEdgeTheRearIs() {
        for (int dy : new int[]{1, -1}) {
            RidgePlanner.Band band = CordonPlan.ridgeBand(REDOUBTS, new int[]{0, dy}, WIDTH);
            // Travelling east, the left of the path is +y; a ramp climbs from that side when rampSide is +1.
            assertEquals(-dy, band.rampSide(), "the rear is the side away from the front");
            assertEquals(CordonPlan.RIDGE_THICKNESS, band.thickness());
            double y = (REDOUBTS.get(0)[1] + REDOUBTS.get(0)[3]) / 2 + dy * CordonPlan.RIDGE_FORWARD;
            assertEquals(y, band.path().get(1).y(), "lies toward the front of the course");
        }
    }

    @Test
    void holdsTheSameCourseThickerOffTheRedoubtsSeeds() {
        int[] front = {0, -1};
        List<RidgePlanner.Band> held = CordonPlan.heldBands(REDOUBTS, front, WIDTH);
        assertEquals(1, held.size());
        assertEquals(CordonPlan.HELD_THICKNESS, held.get(0).thickness());
        assertEquals(CordonPlan.ridgeBand(REDOUBTS, front, WIDTH).path(), held.get(0).path());
        assertEquals(List.of(), CordonPlan.heldBands(REDOUBTS.subList(0, 3), front, WIDTH),
                "a line short of a redoubt holds nothing");
    }
}
