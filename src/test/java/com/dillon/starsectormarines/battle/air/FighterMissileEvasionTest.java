package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FighterMissileEvasionTest {
    private record Handling(float maxSpeed, float accel, float brakingAccel, float maxTurnRateDegPerSec,
                            float lateralDriftDamping, float stationDamping) implements AirHandling {}
    private static final AirHandling FLIGHT = new Handling(20, 30, 25, 60, 2.5f, 4);
    private static final FighterMissileEvasion.Threat HEAD_ON = new FighterMissileEvasion.Threat(30, 0, -20, 0);

    @Test void warningRequiresClosingThreatInsideTheNearInterceptHorizon() {
        assertTrue(FighterMissileEvasion.incoming(0, 0, 10, 0, HEAD_ON));
        assertFalse(FighterMissileEvasion.incoming(0, 0, 10, 0,
                new FighterMissileEvasion.Threat(-30, 0, 5, 0)), "outpaced tail shot");
        assertFalse(FighterMissileEvasion.incoming(0, 0, 10, 0,
                new FighterMissileEvasion.Threat(30, 30, -20, 0)), "wide crossing shot");
        assertFalse(FighterMissileEvasion.incoming(0, 0, 10, 0,
                new FighterMissileEvasion.Threat(300, 0, -20, 0)), "distant round");
    }

    @Test void delayedBreakIsBoundedAndReturnsSteeringToItsOrdinaryOwner() {
        var state = new FighterMissileEvasion();
        var body = new AirBody(); body.teleport(0, 0, -90); body.vx = 10;
        float dt = 1f / 30;
        for (int i = 0; i < 5; i++) assertNull(state.advance(dt, 0, body, FLIGHT, HEAD_ON));
        var goal = state.advance(dt, 0, body, FLIGHT, HEAD_ON);
        assertNotNull(goal); assertEquals(1, state.breaks);
        int moved = 0;
        while (goal != null) {
            float before = body.facingDegrees;
            AirSteeringSystem.steer(body, goal.x(), goal.y(), SteeringMode.CRUISE, FLIGHT, dt);
            assertTrue(Math.abs(body.angVelDegPerSec) <= FLIGHT.maxTurnRateDegPerSec() + .001f);
            assertNotEquals(before, body.facingDegrees);
            moved++;
            goal = state.advance(dt, 0, body, FLIGHT, null);
        }
        assertTrue(moved >= 25 && moved <= 27, "finite .85-second maneuver");
        assertEquals(moved, state.steeringTicks);
        for (int i = 0; i < 50; i++) assertNull(state.advance(dt, 0, body, FLIGHT, null));
        assertEquals(1, state.breaks, "no invented repeated warning after threat passes");
    }

    @Test void vanishedWarningDoesNotSpendAReactionLater() {
        var state = new FighterMissileEvasion(); var body = new AirBody();
        assertNull(state.advance(.1f, 1, body, FLIGHT, HEAD_ON));
        assertNull(state.advance(1, 1, body, FLIGHT, null));
        assertEquals(0, state.breaks); assertEquals(0f, state.reactionLeft);
    }
    @Test void headOnJinkKeepsForwardProgressRatherThanBrakingTowardAReverseGoal() {
        var state = new FighterMissileEvasion();
        var body = new AirBody(); body.teleport(0, 0, -90); body.vx = 20;
        float dt = 1f / 30;
        FighterMissileEvasion.Goal goal = null;
        for (int i = 0; i < 12 && goal == null; i++) goal = state.advance(dt, 0, body, FLIGHT, HEAD_ON);
        assertNotNull(goal);
        assertTrue(goal.x() > body.x, "the maneuver goal stays ahead of the original nose");
        assertTrue(Math.abs(Math.toDegrees(Math.atan2(goal.y() - body.y, goal.x() - body.x))) < 45,
                "a moderate lateral jink rather than a near-reversing break");
        float initialX = body.x;
        int moves = 0;
        while (goal != null) {
            AirSteeringSystem.steer(body, goal.x(), goal.y(), SteeringMode.CRUISE, FLIGHT, dt);
            moves++;
            goal = state.advance(dt, 0, body, FLIGHT, null);
        }
        assertTrue(body.x - initialX > 20f * moves * dt * .65f,
                "the actual hull keeps substantial forward travel through the weave");
        assertEquals(moves, state.steeringTicks);
    }

}
