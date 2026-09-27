package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceResolutionTest {
    @Test
    void baselineEightCellBlocksRetainOriginalFloatBitsIncludingGroupingAndCutoff() {
        NavigationGrid grid = openGrid(40, 24);
        InfluenceTopology topology = new InfluenceTopology(grid, 8);
        float[] actual = InfluenceFieldBuilder.propagate(topology, List.of(
                new InfluenceSource(1, 1, 1f), new InfluenceSource(2, 2, 1f),
                new InfluenceSource(10, 1, 0.7f), new InfluenceSource(32, 16, 0.2f)));
        for (int y = 0; y < topology.blockHeight(); y++) {
            for (int x = 0; x < topology.blockWidth(); x++) {
                // Original group order and arithmetic, evaluated independently
                // from the builder's attenuation table on an open block graph.
                float expected = 0f;
                expected += legacyValue(1f, x + y) * 2;
                expected += legacyValue(0.7f, Math.abs(x - 1) + y);
                expected += legacyValue(0.2f, Math.abs(x - 4) + Math.abs(y - 2));
                assertEquals(Float.floatToIntBits(expected),
                        Float.floatToIntBits(actual[y * topology.blockWidth() + x]));
            }
        }

        // Exercise the pow fallback past the cached attenuation table too.
        InfluenceTopology longLane = new InfluenceTopology(openGrid(600, 8), 8);
        float[] longField = InfluenceFieldBuilder.propagate(longLane,
                List.of(new InfluenceSource(1, 1, 100_000_000f)));
        for (int steps = 0; steps < longLane.blockCount(); steps++) {
            assertEquals(Float.floatToIntBits(legacyValue(100_000_000f, steps)),
                    Float.floatToIntBits(longField[steps]));
        }
    }

    @Test
    void alignedOpenGroundSamplesKeepTheSamePhysicalAttenuationAtEightAndSixteen() {
        NavigationGrid grid = openGrid(192, 16);
        InfluenceTopology fine = new InfluenceTopology(grid, 8);
        InfluenceTopology coarse = new InfluenceTopology(grid, 16);
        assertEquals(8, fine.blockSize());
        assertEquals(16, coarse.blockSize());
        List<InfluenceSource> sources = List.of(new InfluenceSource(1, 1, 1f));
        float[] eight = InfluenceFieldBuilder.propagate(fine, sources);
        float[] sixteen = InfluenceFieldBuilder.propagate(coarse, sources);
        for (int x = 0; x < grid.getWidth(); x += 16) {
            assertEquals(eight[x / 8], sixteen[x / 16],
                    "Aligned sample at world x=" + x);
        }
        assertEquals((float) Math.pow(InfluenceFieldBuilder.ATTENUATION, 2), sixteen[1]);
        assertEquals(0f, sixteen[10], "Physical propagation cutoff must not double in range");
    }

    @Test
    void sealedRoomsRemainDisconnectedAlthoughABlockScalarCannotDistinguishThem() {
        NavigationGrid grid = openGrid(32, 16);
        for (int y = 0; y < 16; y++) grid.setWalkable(7, y, false);
        for (int blockSize : new int[]{8, 16}) {
            InfluenceTopology topology = new InfluenceTopology(grid, blockSize);
            int source = topology.componentsForCell(2, 3)[0];
            int otherRoom = topology.componentsForCell(12, 3)[0];
            assertNotEquals(source, otherRoom);
            assertFalse(connected(topology, source, otherRoom));
            float[] field = InfluenceFieldBuilder.propagate(topology,
                    List.of(new InfluenceSource(2, 3, 1f)));
            assertEquals(0f, field[18 / blockSize],
                    "The disconnected other room must not relay influence onward");
            if (blockSize == 16) {
                assertEquals(topology.blockForComponent(source), topology.blockForComponent(otherRoom));
                assertEquals(1f, field[topology.blockForComponent(otherRoom)],
                        "Known quantization: the per-block maximum reads high in both sealed rooms");
            }
        }
    }

    @Test
    void sharedEdgeBarrierIsPreservedAndAnOpenedDoorConnectsAtEitherResolution() {
        NavigationGrid grid = openGrid(32, 16);
        for (int y = 0; y < 16; y++) grid.setSharedEdgePassable(7, y, Direction.E, false);
        for (int blockSize : new int[]{8, 16}) {
            InfluenceTopology sealed = new InfluenceTopology(grid, blockSize);
            assertFalse(connected(sealed, sealed.componentsForCell(2, 3)[0],
                    sealed.componentsForCell(18, 3)[0]));
        }
        grid.setSharedEdgePassable(7, 3, Direction.E, true);
        grid.setDoorway(8, 3, true);
        for (int blockSize : new int[]{8, 16}) {
            InfluenceTopology open = new InfluenceTopology(grid, blockSize);
            assertTrue(connected(open, open.componentsForCell(2, 3)[0],
                    open.componentsForCell(18, 3)[0]));
            float[] field = InfluenceFieldBuilder.propagate(open,
                    List.of(new InfluenceSource(2, 3, 1f)));
            assertEquals((float) Math.pow(InfluenceFieldBuilder.ATTENUATION, 2), field[18 / blockSize]);
        }
    }

    @Test
    void optionalWorkTotalsExposeGroupedTraversalReductionWithoutChangingTheField() {
        NavigationGrid grid = openGrid(64, 64);
        List<InfluenceSource> sources = List.of(new InfluenceSource(1, 1, 1f),
                new InfluenceSource(2, 2, 1f), new InfluenceSource(10, 2, 1f));
        InfluenceTopology fine = new InfluenceTopology(grid, 8);
        InfluenceTopology coarse = new InfluenceTopology(grid, 16);
        InfluenceFieldBuilder.Work work = new InfluenceFieldBuilder.Work();
        float[] measured = InfluenceFieldBuilder.propagate(fine, sources, work);
        assertEquals(2, work.sourceGroups);
        assertEquals(128, work.visitedComponents);
        float[] ordinary = InfluenceFieldBuilder.propagate(fine, sources);
        for (int i = 0; i < ordinary.length; i++) {
            assertEquals(Float.floatToIntBits(ordinary[i]), Float.floatToIntBits(measured[i]));
        }
        InfluenceFieldBuilder.propagate(coarse, sources, work);
        assertEquals(3, work.sourceGroups, "Caller-owned totals accumulate across channels");
        assertEquals(144, work.visitedComponents);
    }

    private static float legacyValue(float magnitude, int steps) {
        float value = magnitude * (float) Math.pow(InfluenceFieldBuilder.ATTENUATION, steps);
        return value < InfluenceFieldBuilder.MIN_PROPAGATED_VALUE ? 0f : value;
    }

    private static boolean connected(InfluenceTopology topology, int from, int to) {
        boolean[] seen = new boolean[topology.componentCount()];
        int[] queue = new int[seen.length];
        int head = 0, tail = 0;
        seen[from] = true;
        queue[tail++] = from;
        while (head < tail) {
            int component = queue[head++];
            if (component == to) return true;
            for (int neighbor : topology.neighbors(component)) {
                if (seen[neighbor]) continue;
                seen[neighbor] = true;
                queue[tail++] = neighbor;
            }
        }
        return false;
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
