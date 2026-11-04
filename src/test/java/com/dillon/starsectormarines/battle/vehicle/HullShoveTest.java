package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullShoveTest {

    /** Open ground but for a block whose corner a technical's right flank is shoved onto. */
    private static NavigationGrid cornerBlock() {
        NavigationGrid grid = new NavigationGrid(24, 24);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                if (x < 11 || x > 13 || y < 3 || y > 5) grid.setWalkableFloor(x, y);
            }
        }
        return grid;
    }

    private static GroundBody technicalAt(float x, float y, float facing) {
        GroundBody body = VehicleType.MILITIA_TECHNICAL.createBody();
        body.x = x;
        body.y = y;
        body.facingDegrees = facing;
        return body;
    }

    @Test
    void aHullShovedOntoACornerCanStillBackAlongTheFlankOnIt() {
        NavigationGrid grid = cornerBlock();
        VehicleType type = VehicleType.MILITIA_TECHNICAL;
        float[] unmet = new float[2];
        // Exact contact rounds differently from each start, so one shove alone can pass by luck.
        for (int i = 0; i < 40; i++) {
            for (int facing = 0; facing < 10; facing++) {
                GroundBody body = technicalAt(9.6f, 6.3f + i * .005f, -32.06f + facing * .03f);
                for (int push = 0; push < 12; push++) HullShove.move(body, type, grid, .1f, 0f, false, unmet);
                assertTrue(unmet[0] > 0f, "the corner stopped it");
                assertEquals(VehicleController.REVERSE_RECOVERY_CELLS,
                        VehicleController.maxReverseDistance(body.x, body.y, body.facingDegrees, type, grid), .001f,
                        "start " + i + ", facing " + facing);
            }
        }
    }

    @Test
    void aHullAlreadyStoppedShortDoesNotCreepBack() {
        NavigationGrid grid = cornerBlock();
        VehicleType type = VehicleType.MILITIA_TECHNICAL;
        GroundBody body = technicalAt(9.6f, 6.4f, -32.06f);
        float[] unmet = new float[2];
        HullShove.move(body, type, grid, 1.2f, 0f, false, unmet);
        float x = body.x;

        HullShove.move(body, type, grid, 1.2f, 0f, false, unmet);

        assertEquals(x, body.x, .001f);
    }
}
