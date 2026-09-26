package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteCostFieldTest {

    @Test
    void denseSnapshotRetainsItsLogicalSizeValuesAndRevision() {
        RouteCostField field = new RouteCostField(new float[]{1f, 1.125f, 2f}, 73L);
        assertEquals(3, field.size());
        assertEquals(3, field.storedValueCount());
        assertEquals(73L, field.revision());
        assertEquals(1f, field.costAt(0));
        assertEquals(1.125f, field.costAt(1));
        assertEquals(2f, field.costAt(2));
    }

    @Test
    void blockStorageMatchesDenseBitsIncludingPartialEdgesAndNonPowerOfTwoBlocks() {
        assertDenseEquivalent(17, 11, 3);
        assertDenseEquivalent(8, 5, 1);
        assertDenseEquivalent(3, 2, 7);
        assertDenseEquivalent(16, 8, 4);
    }

    private static void assertDenseEquivalent(int width, int height, int blockSize) {
        RouteCostField.BlockLayout layout = new RouteCostField.BlockLayout(width, height, blockSize);
        float[] blocks = new float[layout.blockWidth() * layout.blockHeight()];
        for (int i = 0; i < blocks.length; i++) blocks[i] = 1f + i / (i + 6f);
        RouteCostField compact = layout.snapshot(blocks, 91L);
        float[] dense = new float[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                dense[y * width + x] = blocks[(y / blockSize) * layout.blockWidth() + x / blockSize];
            }
        }
        RouteCostField expanded = new RouteCostField(dense, 92L);
        assertEquals(expanded.size(), compact.size());
        assertEquals(91L, compact.revision());
        for (int i = 0; i < dense.length; i++) {
            assertEquals(Float.floatToRawIntBits(expanded.costAt(i)),
                    Float.floatToRawIntBits(compact.costAt(i)), "cell " + i);
            assertEquals(Float.floatToRawIntBits(expanded.costAt(i)),
                    Float.floatToRawIntBits(compact.costAt(i, i % width, i / width)), "coordinate cell " + i);
        }
        assertThrows(IndexOutOfBoundsException.class, () -> compact.costAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> compact.costAt(compact.size()));
    }

    @Test
    void conquestSnapshotStoresSixtyFourTimesFewerMultipliers() {
        RouteCostField.BlockLayout layout = new RouteCostField.BlockLayout(560, 336, 8);
        RouteCostField field = layout.snapshot(new float[70 * 42], 1L);
        assertEquals(188_160, field.size());
        assertEquals(2_940, field.storedValueCount());
        assertEquals(field.size(), 64 * field.storedValueCount());
    }

    @Test
    void sharedLayoutDoesNotShareMutableSnapshotValues() {
        RouteCostField.BlockLayout layout = new RouteCostField.BlockLayout(5, 3, 3);
        RouteCostField old = layout.snapshot(new float[]{1.25f, 1.5f}, RouteCostField.nextRevision());
        RouteCostField next = layout.snapshot(new float[]{1.75f, 1f}, RouteCostField.nextRevision());
        assertEquals(1.25f, old.costAt(0));
        assertEquals(1.5f, old.costAt(14));
        assertEquals(1.75f, next.costAt(0));
        assertEquals(1f, next.costAt(14));
        assertTrue(next.revision() > old.revision());
    }

    @Test
    void invalidLayoutsAndMismatchedBlockArraysFailBeforePublication() {
        assertThrows(IllegalArgumentException.class, () -> new RouteCostField.BlockLayout(0, 4, 2));
        assertThrows(IllegalArgumentException.class, () -> new RouteCostField.BlockLayout(4, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new RouteCostField.BlockLayout(4, 4, 0));
        assertThrows(ArithmeticException.class,
                () -> new RouteCostField.BlockLayout(Integer.MAX_VALUE, 2, 8));
        RouteCostField.BlockLayout layout = new RouteCostField.BlockLayout(5, 7, 3);
        assertThrows(IllegalArgumentException.class, () -> layout.snapshot(new float[5], 1L));
        assertThrows(IllegalArgumentException.class, () -> layout.snapshot(new float[7], 1L));
        assertThrows(NullPointerException.class, () -> layout.snapshot(null, 1L));
    }
}
