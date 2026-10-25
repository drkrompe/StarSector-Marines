package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreightYardTest {

    @Test
    void everyPoseLaysTheWholeFootprintOnceAndOpensTheGateOutward() {
        for (int turn = 0; turn < 4; turn++) {
            FreightYard.Pose pose = new FreightYard.Pose(10, 20, turn);
            Set<Long> cells = new HashSet<>();
            for (int y = 0; y < FreightYard.DEPTH; y++) {
                for (int x = 0; x < FreightYard.WIDTH; x++) {
                    int[] c = pose.cell(x, y);
                    assertTrue(c[0] >= 10 && c[0] < 10 + pose.width() && c[1] >= 20 && c[1] < 20 + pose.height(),
                            "turn " + turn + " put " + x + "," + y + " outside its footprint");
                    cells.add(((long) c[0] << 32) | c[1]);
                }
            }
            assertEquals(FreightYard.WIDTH * FreightYard.DEPTH, cells.size(), "turn " + turn + " laid a cell twice");
            int[] mouth = pose.cell(FreightYard.WIDTH / 2, -1);
            assertFalse(cells.contains(((long) mouth[0] << 32) | mouth[1]), "turn " + turn + " gate opens inward");
        }
    }

    @Test
    void theDriveFromTheBayToTheGateIsClearOfFreight() {
        // A hauler leaves straight out, and needs room either side of the centre line to set out at all.
        for (int y = 0; y < FreightYard.YARD; y++) {
            for (int x = FreightYard.WIDTH / 2 - 3; x < FreightYard.WIDTH / 2 + 3; x++) {
                assertFalse(FreightYard.stack(x, y), "freight in the drive at " + x + "," + y);
            }
        }
        for (int x = 0; x < FreightYard.WIDTH; x++) {
            if (FreightYard.onGate(x)) assertTrue(Math.abs(x + .5f - FreightYard.WIDTH / 2f) < FreightYard.GATE / 2f);
        }
    }
}
