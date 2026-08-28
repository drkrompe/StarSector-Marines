package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A packed fortress building is walled and windowed, not merely carved.
 *
 * <p>One pack into a bare envelope, which is all these invariants are about.
 * The packer leaves a room's wall as ground it did not carve, so on a map the
 * wall has to be authored — a mask saying which of its faces are outside, and
 * apertures where the garrison fights from. Both are silent failures: an
 * unauthored ring is an invisible obstruction rather than a missing exception,
 * and a building with no windows looks finished and cannot be defended.
 */
class FortressInteriorWallsTest {

    private static final int W = 90;
    private static final int H = 70;

    @Test
    void everyPackedBuildingIsWalledAndTheHabitableOnesAreWindowed() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        GenContext ctx = new GenContext(grid, topology, new Random(5L), W, H, 5L);

        boolean[][] ground = new boolean[W][H];
        for (int x = 2; x < W - 2; x++) {
            for (int y = 2; y < H - 2; y++) ground[x][y] = true;
        }
        boolean[][] muster = new boolean[W][H];
        for (int y = 2; y < 10; y++) {
            muster[W / 2][y] = true;
            muster[W / 2 + 1][y] = true;
        }

        FortressInterior.Result result = FortressInterior.pack(
                ctx, ground, muster, TraversalAxis.SOUTH_TO_NORTH, FortressProgram.ward());
        assertTrue(result.placed().size() >= 2, "nothing was packed, so nothing is proved");

        Set<Long> floors = new HashSet<>();
        for (RoomPacker.Placed room : result.placed()) {
            for (int[] cell : room.shape().filled()) {
                floors.add(key(room.originX() + cell[0], room.originY() + cell[1]));
            }
        }

        int unmasked = 0;
        for (RoomPacker.Placed room : result.placed()) {
            Set<Long> footprint = new HashSet<>();
            for (int[] cell : room.shape().filled()) {
                footprint.add(key(room.originX() + cell[0], room.originY() + cell[1]));
            }
            for (int[] cell : room.shape().wall()) {
                footprint.add(key(room.originX() + cell[0], room.originY() + cell[1]));
            }
            for (int[] cell : room.shape().wall()) {
                int x = room.originX() + cell[0];
                int y = room.originY() + cell[1];
                if (!grid.inBounds(x, y) || grid.isWalkable(x, y)) continue;
                // A cell with no face on the outside is buried in the building's
                // own mass — the gatehouse's enclosed courtyard is six of them —
                // and an empty mask is the right answer there.
                if (!facesOutside(footprint, x, y)) continue;
                if (topology.getWallDirMask(x, y) == 0) unmasked++;
            }
        }
        assertEquals(0, unmasked,
                "a solid ring cell with a face on the outside and no wall mask renders "
                        + "as blank fill: an invisible obstruction with the room's fill "
                        + "standing in the open");

        int windows = 0;
        for (SharedEdgeBarrier barrier : grid.getEdgeBarriers()) {
            if (barrier.kind() != SharedEdgeBarrier.Kind.WINDOW) continue;
            windows++;
            // The structure cell is the side the wall was authored from, which
            // is the building's own: the opening is in its wall, and the cell
            // across the edge is what it looks out onto.
            int x = barrier.structureCellX();
            int y = barrier.structureCellY();
            assertTrue(grid.isWalkable(x, y),
                    "a window is a firing position, so its cell has to be standable");
            boolean ownsCanonicalSide = x == barrier.cellX() && y == barrier.cellY();
            int acrossX = ownsCanonicalSide
                    ? barrier.cellX() + barrier.direction().dx : barrier.cellX();
            int acrossY = ownsCanonicalSide
                    ? barrier.cellY() + barrier.direction().dy : barrier.cellY();
            assertTrue(!floors.contains(key(acrossX, acrossY)),
                    "a window opens onto the yard, never into the room next door");
        }
        assertTrue(windows > 0, "a garrison that cannot shoot out of its own buildings "
                + "is a box: the habitable rooms in the program must be windowed");

        boolean windowedPurpose = result.placed().stream()
                .anyMatch(room -> room.purpose() == RoomPurpose.BARRACKS);
        assertTrue(windowedPurpose, "the program stopped placing barracks, so this test "
                + "no longer covers the purpose it names");
    }

    /** Whether any of this cell's four sides looks out of the building. */
    private static boolean facesOutside(Set<Long> footprint, int x, int y) {
        return !footprint.contains(key(x, y + 1)) || !footprint.contains(key(x, y - 1))
                || !footprint.contains(key(x + 1, y)) || !footprint.contains(key(x - 1, y));
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }
}
