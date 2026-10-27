package com.dillon.starsectormarines.battle.setup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThunderRunPressureTest {
    @Test void eachWaveComesNoLaterAndNoSmallerThanTheLast() {
        for (int n = 1; n < ThunderRunPressure.WAVES; n++) {
            assertTrue(ThunderRunPressure.gapSeconds(n) <= ThunderRunPressure.gapSeconds(n - 1), "wave " + n + " comes sooner");
            assertTrue(ThunderRunPressure.waveSize(n) >= ThunderRunPressure.waveSize(n - 1), "wave " + n + " is no smaller");
            assertTrue(ThunderRunPressure.waveSecond(n) > ThunderRunPressure.waveSecond(n - 1), "waves never land together");
        }
        assertTrue(ThunderRunPressure.waveSize(ThunderRunPressure.WAVES - 1) > ThunderRunPressure.waveSize(0), "the pressure rises");
    }
}
