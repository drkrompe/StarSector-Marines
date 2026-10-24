package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartGunsTest {

    @Test
    void aPlaceRunningLowCallsOneTruckWhileAGunStands() {
        assertTrue(HeartGuns.calls(0.4f, true, 0));
        assertFalse(HeartGuns.calls(0.6f, true, 0), "half full or better waits");
        assertFalse(HeartGuns.calls(0.1f, true, 1), "never with its own truck on the road");
        assertFalse(HeartGuns.calls(0f, false, 0), "a place with no gun standing calls nothing");
    }
}
