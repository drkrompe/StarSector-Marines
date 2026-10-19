package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a hull whose gun was shot away withdraws: out of every threat's reach, away from the ones it is inside. */
class VehicleEngagementWithdrawalTest {

    private static final float M = VehicleEngagement.WITHDRAW_MARGIN;

    @Test
    void aHullAlreadyClearOfEveryReachHolds() {
        assertNull(VehicleEngagement.withdrawal(0f, 0f, List.of(new float[] {40f + M + 0.5f, 0f, 40f})));
        assertNull(VehicleEngagement.withdrawal(0f, 0f, List.of()));
    }

    @Test
    void aHullInsideOneReachDrivesStraightAwayFarEnoughToClearIt() {
        // A Bulwark ten cells east with reach 40: thirty-six cells inside the margin.
        float[] away = VehicleEngagement.withdrawal(0f, 0f, List.of(new float[] {10f, 0f, 40f}));
        assertNotNull(away);
        assertEquals(-1f, away[0], 1e-4f);
        assertEquals(0f, away[1], 1e-4f);
        assertEquals(40f + M - 10f + VehicleEngagement.WITHDRAW_OVERSHOOT, away[2], 1e-4f);
        // The point it aims at is clear.
        assertNull(VehicleEngagement.withdrawal(away[0] * away[2], away[1] * away[2],
                List.of(new float[] {10f, 0f, 40f})));
    }

    @Test
    void threatsItIsNotInsideDoNotPushIt() {
        float[] away = VehicleEngagement.withdrawal(0f, 0f, List.of(
                new float[] {0f, 10f, 30f},
                new float[] {200f, 0f, 30f}));
        assertNotNull(away);
        assertEquals(0f, away[0], 1e-4f);
        assertEquals(-1f, away[1], 1e-4f);
    }

    @Test
    void theDeeperIntrusionPushesHarder() {
        // North threat is nearer and longer-reaching than the east one: the heading leans south more than west.
        float[] away = VehicleEngagement.withdrawal(0f, 0f, List.of(
                new float[] {0f, 10f, 40f},
                new float[] {20f, 0f, 30f}));
        assertNotNull(away);
        assertTrue(away[1] < away[0] && away[0] < 0f, "south-west, mostly south: " + away[0] + "," + away[1]);
        assertEquals(1f, Math.hypot(away[0], away[1]), 1e-4f);
    }

    @Test
    void threatsBalancedEitherSideStillGiveAHeading() {
        float[] away = VehicleEngagement.withdrawal(0f, 0f, List.of(
                new float[] {10f, 0f, 30f},
                new float[] {-10f, 0f, 30f}));
        assertNotNull(away);
        assertEquals(1f, Math.hypot(away[0], away[1]), 1e-4f);
    }
}
