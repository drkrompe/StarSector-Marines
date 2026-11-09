package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.VoidBackdrop;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A craft a mission calls to a slip's berth stands there exactly while it is due, flying in before and out after. */
class VoidCallTest {

    private static final VoidBackdrop.Slip SLIP = new VoidBackdrop.Slip(300f, 50f, 140f, 50f, 60f);
    private static final VoidBackdrop.Call CALL = new VoidBackdrop.Call(0, "hull.png", 100f, 200f);

    @Test
    void theCraftIsAbsentUntilItFliesInAndAfterItHasFlownOut() {
        float flight = VoidBackdropRenderSystem.CALL_FLIGHT_SECONDS;
        assertNull(VoidBackdropRenderSystem.callCraft(CALL, SLIP, 100f - flight - 1f));
        assertNotNull(VoidBackdropRenderSystem.callCraft(CALL, SLIP, 100f - flight + 1f));
        assertNotNull(VoidBackdropRenderSystem.callCraft(CALL, SLIP, 200f + flight - 1f));
        assertNull(VoidBackdropRenderSystem.callCraft(CALL, SLIP, 200f + flight));
    }

    @Test
    void itStandsAtTheBerthFromArrivalToDeparture() {
        for (float t : new float[]{100f, 150f, 199f}) {
            float[] c = VoidBackdropRenderSystem.callCraft(CALL, SLIP, t);
            assertEquals(140f, c[0], 1e-3f);
            assertEquals(50f, c[1], 1e-3f);
            assertEquals(60f, c[2], 1e-3f);
        }
    }

    @Test
    void itSlowsAsItComesInAndTurnsForTheWayOutAsItLeaves() {
        float early = 100f - VoidBackdropRenderSystem.CALL_FLIGHT_SECONDS + 5f;
        float[] a = VoidBackdropRenderSystem.callCraft(CALL, SLIP, early);
        float[] b = VoidBackdropRenderSystem.callCraft(CALL, SLIP, early + 5f);
        float[] c = VoidBackdropRenderSystem.callCraft(CALL, SLIP, 95f);
        float[] d = VoidBackdropRenderSystem.callCraft(CALL, SLIP, 100f);
        assertTrue(Math.abs(b[0] - a[0]) > Math.abs(d[0] - c[0]), "covers more ground early than late");
        float in = VoidBackdropRenderSystem.callCraft(CALL, SLIP, 150f)[3];
        float out = VoidBackdropRenderSystem.callCraft(CALL, SLIP, 200f + VoidBackdropRenderSystem.CALL_TURN_SECONDS)[3];
        assertEquals(Math.PI, Math.abs(out - in), 1e-3);
    }
}
