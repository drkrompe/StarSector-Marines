package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreightYardTest {

    @Test
    void everyPoseLaysTheWholeFootprintOnceAndOpensEveryGateOutward() {
        for (FreightYard.Size size : FreightYard.Size.values()) {
            for (int turn = 0; turn < 4; turn++) {
                FreightYard.Pose pose = new FreightYard.Pose(size, 10, 20, turn);
                Set<Long> cells = new HashSet<>();
                for (int y = 0; y < size.depth; y++) {
                    for (int x = 0; x < size.width; x++) {
                        int[] c = pose.cell(x, y);
                        assertTrue(c[0] >= 10 && c[0] < 10 + pose.width() && c[1] >= 20 && c[1] < 20 + pose.height(),
                                size + " turn " + turn + " put " + x + "," + y + " outside its footprint");
                        cells.add(((long) c[0] << 32) | c[1]);
                    }
                }
                assertEquals(size.width * size.depth, cells.size(), size + " turn " + turn + " laid a cell twice");
                for (int bay : size.bays) {
                    int[] mouth = pose.cell(bay, -1);
                    assertFalse(cells.contains(((long) mouth[0] << 32) | mouth[1]),
                            size + " turn " + turn + " gate opens inward");
                }
            }
        }
    }

    @Test
    void everyBayHasAStraightDriveToItsGateClearOfFreight() {
        // A hauler leaves straight out, and needs room either side of the centre line to set out at all.
        for (FreightYard.Size size : FreightYard.Size.values()) {
            for (int bay : size.bays) {
                for (int y = 0; y < size.yard; y++) {
                    for (int x = bay - 3; x < bay + 3; x++) {
                        assertFalse(size.stack(x, y), size + " freight in the drive at " + x + "," + y);
                    }
                }
                for (int x = bay - FreightYard.GATE / 2; x < bay + FreightYard.GATE / 2; x++) {
                    assertTrue(size.onGate(x), size + " gate narrower than a hauler at " + x);
                }
                assertTrue(size.standY() > 1 && size.standY() < size.yard - 1, size + " stand outside the yard");
            }
        }
    }

    @Test
    void theLargerYardIsTriedFirstSoShortGroundGetsTheSmallerOne() {
        FreightYard.Size[] ladder = FreightYard.Size.values();
        for (int i = 1; i < ladder.length; i++) {
            assertFalse(ladder[i - 1].fits(ladder[i].width, ladder[i].depth), ladder[i] + " ground holds " + ladder[i - 1]);
            assertTrue(ladder[i].fits(ladder[i - 1].depth, ladder[i - 1].width), ladder[i - 1] + " ground refuses " + ladder[i]);
        }
    }
}
