package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.StoreSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreBrickCanvasTest {

    @Test
    void everyMarineAndMechSlotHasASymbolAndShuttlePartsHaveNone() {
        for (StoreSlot slot : StoreSlot.values()) {
            boolean shuttle = slot.section() == StoreSlot.Section.BOATS;
            assertEquals(!shuttle, StoreBrickCanvas.has(slot), slot.name());
            if (shuttle) assertNull(StoreBrickCanvas.of(slot), slot.name());
            else assertNotNull(StoreBrickCanvas.of(slot), slot.name());
        }
        assertNull(StoreBrickCanvas.of(null));
    }
}
