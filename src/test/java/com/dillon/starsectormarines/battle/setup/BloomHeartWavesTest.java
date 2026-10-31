package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.evacuation.HeartLanes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class BloomHeartWavesTest {

    private static final int STEP = HeartLanes.STEP_OFF_SECONDS;

    @Test
    void theBurnsWavesComeAtTheLiftsAndAfterTheBarrage() {
        // Lines lifting at 90, 130, 170 and the last at 420: three early waves, then two after the end.
        int[] lifts = {90, 130, 170, 420};
        assertArrayEquals(new int[]{STEP, 90, 130, 170, 420, 480}, BloomHeartSetup.burnWaves(lifts, 3, 2, false));
    }

    @Test
    void atParityAWaveStepsOffEveryMinuteThroughTheWalkToo() {
        int[] lifts = {90, 130, 170, 420};
        assertArrayEquals(new int[]{STEP, 90, 130, 170, 230, 290, 350, 410, 420, 480},
                BloomHeartSetup.burnWaves(lifts, 3, 2, true));
    }
}
