package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MirvSplitTest {

    /** A carrier crossing 10 cells a second. */
    private static double seconds(double cells) {
        return cells / 10.0;
    }

    @Test
    void aLongShotSplitsAtItsSplitRangeWhateverTheFlag() {
        assertEquals(60f, MirvSplit.along(100f, 40f, 2f, false, MirvSplitTest::seconds), 1e-4f);
        assertEquals(60f, MirvSplit.along(100f, 40f, 2f, true, MirvSplitTest::seconds), 1e-4f);
    }

    @Test
    void aCloseTargetWithoutTheFlagFliesOnUntilTheDispenserArms() {
        // 30 cells to go, split range 45: in range at once, but the dispenser needs 2 s (20 cells).
        float along = MirvSplit.along(30f, 45f, 2f, false, MirvSplitTest::seconds);
        assertTrue(seconds(along) >= 2.0, "armed before splitting: " + along);
        assertTrue(along <= MirvSplit.MAX_SHARE * 30f + 1e-4f);
    }

    @Test
    void aCloseTargetWithTheFlagSplitsAtOnceInsideItsRoomToFan() {
        float along = MirvSplit.along(30f, 45f, 2f, true, MirvSplitTest::seconds);
        assertEquals(MirvSplit.MIN_SHARE * 30f, along, 1e-4f);
        assertTrue(seconds(along) < 2.0);
    }

    @Test
    void anUnarmableCarrierStopsShortOfTheTarget() {
        float along = MirvSplit.along(30f, 45f, 99f, false, MirvSplitTest::seconds);
        assertEquals(MirvSplit.MAX_SHARE * 30f, along, 1e-4f);
    }
}
