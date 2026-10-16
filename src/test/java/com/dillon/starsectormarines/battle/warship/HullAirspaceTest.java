package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A station chosen near an airspace edge is eased back over the middle; one in the middle is left alone. */
class HullAirspaceTest {

    private static final HullAirspace BASIN = new HullAirspace(0f, 0f, 300f, 88f);

    @Test
    void aStationAtTheEdgeIsEasedHalfTheSoftBandInward() {
        float[] at = BASIN.settle(-50f, 44f, 10f);
        assertEquals(10f + 0.5f * HullAirspace.SOFT_EDGE_CELLS, at[0], 1e-4f);
        float[] east = BASIN.settle(400f, 44f, 10f);
        assertEquals(290f - 0.5f * HullAirspace.SOFT_EDGE_CELLS, east[0], 1e-4f);
    }

    @Test
    void theMiddleIsUntouchedAndTheEaseIsContinuous() {
        assertEquals(150f, BASIN.settle(150f, 44f, 10f)[0], 1e-6f);
        float inner = 10f + HullAirspace.SOFT_EDGE_CELLS;
        assertEquals(inner, BASIN.settle(inner, 44f, 10f)[0], 1e-4f);
        float a = BASIN.settle(inner - 0.01f, 44f, 10f)[0];
        assertTrue(Math.abs(a - inner) < 0.01f, "no step at the band's inner line");
    }

    @Test
    void aNarrowAxisUsesAQuarterOfItsRoomAsTheBand() {
        // 88 cells tall less a 28-cell margin each side leaves 32; a quarter of that is the band.
        float[] at = BASIN.settle(150f, 0f, 28f);
        assertEquals(28f + 0.5f * 8f, at[1], 1e-4f);
    }
}
