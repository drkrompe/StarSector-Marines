package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IceVoidLookTest {

    @Test
    void theCrevasseFallsToItsDeepBlueAtTheFloor() {
        assertEquals(0f, IceVoidLook.deepTint(0f, 36f), 1e-6f);
        assertTrue(IceVoidLook.deepTint(12f, 36f) < IceVoidLook.deepTint(24f, 36f));
        assertEquals(IceVoidLook.DEEP_MIX, IceVoidLook.deepTint(36f, 36f), 1e-6f);
    }

    @Test
    void snowLiesBackOnALedgeAndIceShowsWhereItBreaks() {
        assertEquals(0f, IceVoidLook.snowCover(0f));
        assertEquals(1f, IceVoidLook.snowCover(IceVoidLook.SNOW_FULL));
    }

    @Test
    void snowBlowsOffTheLipOnlyWhereTheWindLeavesTheGroundForTheDrop() {
        // The drop lies east of the ground; the wind blows east, then west.
        assertTrue(GlacialVoidRenderSystem.windward(1f, 0f, 1f, 0f));
        assertFalse(GlacialVoidRenderSystem.windward(1f, 0f, -1f, 0f));
        assertFalse(GlacialVoidRenderSystem.windward(0f, 0f, 1f, 0f), "no edge, no lip");
    }
}
