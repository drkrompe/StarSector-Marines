package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every kind's sally ports are authored as closed wall between open yard and open ground, on a face with no gate, clear
 * of every nest and room: a port is a door in the wall and nothing else.
 */
class FortSallyPortPlanTest {

    /** A port this near a gate's mouth would be on that gate's face, where the gate already serves. */
    private static final int GATE_FACE_CELLS = 12;

    @Test
    void portsAreWallBetweenTheYardAndOpenGround() {
        for (FortKind kind : FortKind.values()) {
            FortPlan plan = FortPlan.of(kind);
            assertFalse(plan.sallyPorts().isEmpty(), kind + " has a face with no gate and so a sally port");
            for (FortPlan.SallyPort port : plan.sallyPorts()) {
                assertEquals(4, port.cells().size(), kind + " port is two cells along a two-deep wall");
                for (int[] c : port.cells()) {
                    assertTrue(plan.isWall(c[0], c[1]), kind + " port cell " + c[0] + "," + c[1] + " is wall");
                    for (int[] n : plan.nests()) assertFalse(n[0] == c[0] && n[1] == c[1], kind + " port on a nest");
                    for (int[] g : plan.gateCells()) assertFalse(g[0] == c[0] && g[1] == c[1], kind + " port on a gate");
                }
                assertFalse(plan.isWorks(port.mouthX(), port.mouthY()), kind + " port mouth is outside the works");
                int[] in = port.inside();
                assertTrue(plan.isWorks(in[0], in[1]) && !plan.isWall(in[0], in[1]), kind + " port opens on the yard");
                for (FortRoom room : plan.rooms()) {
                    int[] f = room.floor();
                    assertFalse(in[0] >= f[0] - 1 && in[1] >= f[1] - 1 && in[0] <= f[0] + f[2] && in[1] <= f[1] + f[3],
                            kind + " port opens straight into a room");
                }
                for (FortPlan.Gate gate : plan.gates()) {
                    assertTrue(Math.hypot(gate.mouthX() - port.mouthX(), gate.mouthY() - port.mouthY()) > GATE_FACE_CELLS,
                            kind + " port stands on a gate's face");
                }
            }
        }
    }

    @Test
    void theInsideCellIsBehindTheWall() {
        FortPlan.SallyPort port = FortPlan.SallyPort.onFace(10, 20, 0, 1, 2);
        assertEquals(10, port.mouthX());
        assertEquals(21, port.mouthY());
        int[] in = port.inside();
        assertEquals(10, in[0]);
        assertEquals(18, in[1]);
    }
}
