package com.dillon.starsectormarines.battle.ui.panel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The magazine both the strip and the cursor ring draw. */
class MagazineGaugeTest {

    @Test
    void aBurstWeaponIsABeltInBlocksOfItsBurstAndAPacketWeaponShowsShells() {
        MagazineGauge belt = MagazineGauge.of(1, 8, 48, 27, 0f, 4f);
        assertEquals(MagazineGauge.Kind.LINK, belt.kind());
        assertEquals(8, belt.group());
        assertTrue(belt.groupEnd(7));
        assertFalse(belt.groupEnd(8));
        assertFalse(belt.groupEnd(47), "no gap after the last round");
        assertEquals(MagazineGauge.Kind.SHELL, MagazineGauge.of(6, 1, 8, 3, 0f, 3f).kind());
        assertEquals(MagazineGauge.Kind.RIFLE, MagazineGauge.of(1, 1, 4, 1, 0f, 3f).kind());
    }

    @Test
    void roundsLeaveFromTheFirstAndAReloadPressesThemBackIn() {
        MagazineGauge firing = MagazineGauge.of(1, 1, 4, 1, 0f, 3f);
        assertEquals(MagazineGauge.Round.LIVE, firing.round(0));
        assertEquals(MagazineGauge.Round.SPENT, firing.round(1));
        MagazineGauge reloading = MagazineGauge.of(1, 1, 4, 0, 1.5f, 3f);
        assertTrue(reloading.reloading());
        assertEquals(MagazineGauge.Round.LOADING, reloading.round(1));
        assertEquals(MagazineGauge.Round.SPENT, reloading.round(2));
    }

    @Test
    void theLastQuarterReadsLowButAReloadDoesNot() {
        assertTrue(MagazineGauge.of(1, 1, 4, 1, 0f, 3f).low());
        assertFalse(MagazineGauge.of(1, 1, 4, 2, 0f, 3f).low());
        assertTrue(MagazineGauge.of(1, 8, 48, 12, 0f, 4f).low());
        assertFalse(MagazineGauge.of(1, 1, 4, 0, 1f, 3f).low());
    }

    @Test
    void ringPipsFillTheSpanWithAGapBetweenBlocks() {
        MagazineGauge belt = MagazineGauge.of(1, 8, 48, 48, 0f, 4f);
        float end = belt.pipStartDegrees(47) + belt.pipStepDegrees();
        assertEquals(MagazineGauge.RING_HALF_SPAN_DEGREES, end, 1e-3f);
        assertEquals(belt.pipStepDegrees() + MagazineGauge.RING_GROUP_GAP_DEGREES,
                belt.pipStartDegrees(8) - belt.pipStartDegrees(7), 1e-3f);
        assertEquals(-MagazineGauge.RING_HALF_SPAN_DEGREES, MagazineGauge.of(1, 1, 4, 4, 0f, 3f).pipStartDegrees(0), 1e-6f);
    }
}
