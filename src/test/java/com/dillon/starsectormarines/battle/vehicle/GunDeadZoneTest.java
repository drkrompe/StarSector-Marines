package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GunDeadZoneTest {

    @Test
    void aGunWithNoBurstAndNoMinimumHasNone() {
        assertEquals(0f, GunDeadZone.fromMount(0f, 0f, 1.5f, 0.4f));
    }

    @Test
    void aBurstIsKeptOffTheHullWhereverTheMountSits() {
        assertEquals(1.8f + 1.5f + GunDeadZone.MARGIN, GunDeadZone.fromMount(0f, 1.8f, 1.5f, 0f), 1e-6f);
        assertEquals(1.8f + 1.5f + GunDeadZone.MARGIN + 0.4f, GunDeadZone.fromMount(0f, 1.8f, 1.5f, 0.4f), 1e-6f);
    }

    @Test
    void theMinimumRangeWinsWhereItReachesFurther() {
        assertEquals(14f, GunDeadZone.fromMount(14f, 1.4f, 1.5f, 0.4f));
    }
}
