package com.dillon.starsectormarines.battle.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitLogTest {

    private static HitLog.Hit hit(int tick) {
        return new HitLog.Hit(tick, 2, "DEFENDER:TURRET", 1, "MARINE:MARINE", 10f, 10f, 4f, 18f, "FRONT", null,
                0f, 3f, 0f, 100f, 47f, false);
    }

    @Test void takesRowsOnlyWhileRecording() {
        HitLog log = new HitLog();
        log.record(hit(1));
        assertTrue(log.hits().isEmpty());
        log.start();
        log.record(hit(2));
        log.stop();
        log.record(hit(3));
        assertEquals(1, log.hits().size());
        assertEquals(2, log.hits().get(0).tick());
    }

    @Test void stopsAtCapacityAndSaysSo() {
        HitLog log = new HitLog();
        log.setCapacity(2);
        log.start();
        for (int i = 0; i < 5; i++) log.record(hit(i));
        assertEquals(2, log.hits().size());
        assertTrue(log.overflowed());
        assertFalse(log.recording());
        log.clear();
        assertFalse(log.overflowed());
    }

    @Test void stampsTheClock() {
        HitLog log = new HitLog();
        int[] tick = {7};
        log.setClock(() -> tick[0]);
        assertEquals(7, log.tick());
        tick[0] = 8;
        assertEquals(8, log.tick());
    }

    @Test void appliedIsArmorPlusStructure() {
        HitLog.Hit hit = new HitLog.Hit(1, 2, "t", 1, "a", 50f, 50f, 30f, 10f, null, null, 0f, 20f, 30f, 0f, 0f, true);
        assertEquals(50f, hit.applied());
    }
}
