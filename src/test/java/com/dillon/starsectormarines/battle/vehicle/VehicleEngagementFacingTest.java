package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a bicycle hull turns its nose back onto a machine off its front arc, without driving at it. */
class VehicleEngagementFacingTest {

    private static final float DT = 1f / 30f;

    @Test
    void aTargetInsideTheStartArcIsLeftAloneAndATurnRunsOnToSquare() {
        assertFalse(VehicleEngagement.shouldReface(30f, false));
        assertTrue(VehicleEngagement.shouldReface(-40f, false));
        assertTrue(VehicleEngagement.shouldReface(30f, true));
        assertFalse(VehicleEngagement.shouldReface(8f, true));
    }

    @Test
    void aTargetOnTheBeamIsFacedByBackingAwayFromIt() {
        // Target due east of a hull pointing north (0 deg is +Y): ninety degrees off the nose, to the right.
        float[] result = turnToward(0f, 30f, 0f, false);
        assertTrue(result[0] <= VehicleEngagement.REFACE_STOP_DEGREES, "faced to " + result[0]);
        assertTrue(result[1] > 30f, "backed away, ended " + result[1] + " cells off");
    }

    @Test
    void justPastTheBeamItStillBacksAndOnlyWellBehindDoesItDriveForward() {
        float edge = VehicleEngagement.BACKING_TURN_DEGREES;
        assertTrue(VehicleEngagement.faceDrive(edge - 5f)[0] < 0f);
        assertTrue(VehicleEngagement.faceDrive(-(edge - 5f))[0] < 0f);
        assertTrue(VehicleEngagement.faceDrive(edge + 5f)[0] > 0f);
    }

    @Test
    void aTargetBehindIsFacedWithAForwardTurnRatherThanByReversingIntoIt() {
        // Target due south of a hull pointing north.
        float[] result = turnToward(0f, 0f, -30f, true);
        assertTrue(result[0] <= VehicleEngagement.REFACE_STOP_DEGREES, "faced to " + result[0]);
        assertTrue(result[2] >= 30f - 1e-3f, "never closed: nearest " + result[2]);
    }

    @Test
    void aStandingTankSquaresUpToAGunOffItsBeamWithoutLeavingItsMark() {
        // A gun 30 cells off, just short of the backing limit round to the right of a hull pointing north: the worst a backing turn faces.
        double bearing = Math.toRadians(5f - VehicleEngagement.BACKING_TURN_DEGREES);
        float[] result = turnToward(0f, (float) -Math.sin(bearing) * 30f, (float) Math.cos(bearing) * 30f, false);
        assertTrue(result[0] <= VehicleEngagement.REFACE_STOP_DEGREES, "faced to " + result[0]);
        assertTrue(result[3] <= VehicleEngagement.STAND_TURN_GROUND, "gave " + result[3] + " cells");
    }

    /**
     * Drives a breakthrough tank's body, set down at the origin facing {@code facing}, with {@code faceDrive} (a
     * {@code forward} turn kept forward, as a turn begun with the target well behind is) until
     * its nose is within the stop arc of ({@code tx}, {@code ty}) or twenty seconds pass: {@code {final degrees off
     * the nose, final distance, nearest distance, ground given}}.
     */
    private static float[] turnToward(float facing, float tx, float ty, boolean forward) {
        GroundBody body = VehicleType.BREAKTHROUGH_TANK.createBody();
        body.teleport(0f, 0f, facing);
        float nearest = Float.MAX_VALUE;
        float alpha = 180f;
        for (int tick = 0; tick < 20 * 30; tick++) {
            alpha = ((AirBody.facingToward(tx - body.x, ty - body.y) - body.facingDegrees + 540f) % 360f) - 180f;
            nearest = Math.min(nearest, (float) Math.hypot(tx - body.x, ty - body.y));
            if (Math.abs(alpha) <= VehicleEngagement.REFACE_STOP_DEGREES) break;
            float[] drive = VehicleEngagement.faceDrive(alpha, forward);
            ((BicycleBody) body).tickManual(drive[0], drive[1], DT);
        }
        return new float[] {Math.abs(alpha), (float) Math.hypot(tx - body.x, ty - body.y), nearest,
                (float) Math.hypot(body.x, body.y)};
    }
}
