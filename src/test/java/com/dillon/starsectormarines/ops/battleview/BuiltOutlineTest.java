package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BuiltOutlineTest {

    private static final int W = 14, H = 12;
    private static final BuildingFaces.Segment[] KINDS = BuildingFaces.Segment.values();

    /**
     * Walls destroyed side by side do not stand fresh faces up inside the building: its faces keep the line they were
     * built on, and the destroyed cells show on it as breaches, not doors.
     */
    @Test
    void destroyedWallsBreachTheBuiltLineInsteadOfStandingNewFacesInside() {
        CellTopology topology = new CellTopology(W, H);
        Building b = house(topology);
        int[] roof = RoofPlans.roofedCells(topology, b, new int[W * H]);
        float[] built = BattleRenderer.builtOutline(topology, roof, false);

        // Two wall cells of the south wall knocked out, side by side, as a breach leaves them.
        for (int x = 5; x <= 6; x++) {
            topology.setWall(x, 2, false);
            topology.setGroundKind(x, 2, CellTopology.GroundKind.RUBBLE);
        }
        // Read from the cells as they now are, the outline would step in behind the hole.
        float[] live = BattleRenderer.builtOutline(topology, RoofPlans.roofedCells(topology, b, new int[W * H]), false);
        assertNotEquals(built.length, live.length, "the live outline moves");

        float[] now = BattleRenderer.stamp(built, topology, new BitSet());
        assertEquals(built.length, now.length);
        int breaches = 0;
        for (int e = 0; e < now.length; e += BuildingFaces.SEG) {
            float[] geometry = new float[7], was = new float[7];
            System.arraycopy(now, e, geometry, 0, 7);
            System.arraycopy(built, e, was, 0, 7);
            assertArrayEquals(was, geometry, "the built line, piece by piece");
            int cell = (int) now[e + 6], x = cell % W, y = cell / W;
            BuildingFaces.Segment kind = KINDS[((int) now[e + 7] >> 2) % KINDS.length];
            if (y == 2 && (x == 5 || x == 6)) {
                assertEquals(BuildingFaces.Segment.BREACH, kind, "cell " + x);
                breaches++;
            } else {
                assertNotEquals(BuildingFaces.Segment.BREACH, kind, "cell " + x + "," + y);
            }
        }
        assertEquals(2, breaches);
    }

    /** A house: floor cells 3..10 x 3..8 inside a ring of wall, all one building. */
    private static Building house(CellTopology topology) {
        int x0 = 3, x1 = 10, y0 = 3, y1 = 8;
        int n = (x1 - x0 + 1) * (y1 - y0 + 1);
        int[] cx = new int[n], cy = new int[n];
        int at = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                topology.setBuildingId(x, y, 1);
                cx[at] = x;
                cy[at++] = y;
            }
        }
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            topology.setWall(x, y0 - 1, true);
            topology.setWall(x, y1 + 1, true);
        }
        for (int y = y0; y <= y1; y++) {
            topology.setWall(x0 - 1, y, true);
            topology.setWall(x1 + 1, y, true);
        }
        return new Building(1, BuildingKind.RESIDENTIAL, x0, x1, y0, y1, cx, cy, .5f, .5f, .5f);
    }
}
