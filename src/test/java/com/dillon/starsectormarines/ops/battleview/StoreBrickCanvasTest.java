package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.StoreSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreBrickCanvasTest {

    @Test
    void everySlotHasASymbol() {
        for (StoreSlot slot : StoreSlot.values()) {
            assertTrue(StoreBrickCanvas.has(slot), slot.name());
            assertNotNull(StoreBrickCanvas.of(slot), slot.name());
        }
        assertNull(StoreBrickCanvas.of(null));
    }
}
