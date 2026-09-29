package com.dillon.starsectormarines.battle.mech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ManualMechDriveTest {
    @Test
    void constantInputHasTheSameVelocityAndTravelAtDifferentTickSizes() {
        ManualMechDrive whole = new ManualMechDrive(), split = new ManualMechDrive();
        var once = whole.advance(1f, 1f, 2f, 1.8f, .4f);
        float dx = 0f, dy = 0f;
        for (int i = 0; i < 12; i++) {
            var step = split.advance(1f, 1f, 2f, 1.8f, .4f / 12f);
            dx += step.dx(); dy += step.dy();
        }
        assertEquals(whole.velocityX(), split.velocityX(), 1e-6f);
        assertEquals(once.dx(), dx, 1e-6f);
        assertEquals(once.dy(), dy, 1e-6f);
        assertTrue(Math.hypot(whole.velocityX(), whole.velocityY()) < 2f);
    }

    @Test
    void relativeChassisMassControlsAccelerationBrakingAndReversal() {
        float previous = Float.POSITIVE_INFINITY;
        for (MechVariant variant : new MechVariant[]{MechVariant.HOUND, MechVariant.SIROCCO, MechVariant.BULWARK}) {
            ManualMechDrive drive = new ManualMechDrive();
            drive.advance(1f, 0f, 1f, variant.relativeMass, .1f);
            assertTrue(drive.velocityX() < previous);
            previous = drive.velocityX();
            drive.advance(1f, 0f, 1f, variant.relativeMass, 2f);
            var reversed = drive.advance(-1f, 0f, 1f, variant.relativeMass, 1f / 30f);
            assertTrue(drive.velocityX() > 0f, "reversal first arrests existing travel");
            assertTrue(reversed.dx() > 0f);
            drive.advance(0f, 0f, 1f, variant.relativeMass, .1f);
            assertTrue(drive.velocityX() > 0f, "release coasts before stopping");
        }
    }

    @Test
    void contactCancelsOnlyClippedMotionIncludingTheFirstPartialContact() {
        ManualMechDrive drive = new ManualMechDrive();
        var step = drive.advance(1f, 1f, 2f, 1f, .5f);
        float tangent = drive.velocityY();
        drive.acceptMotion(step, step.dx() * .5f, step.dy(), .5f);
        assertEquals(0f, drive.velocityX());
        assertEquals(tangent, drive.velocityY());
        var neutral = drive.advance(0f, 0f, 2f, 1f, 1f / 30f);
        assertEquals(0f, neutral.dx());
        assertTrue(neutral.dy() > 0f);
    }

    @Test
    void liveSpeedReductionCapsBothMomentumAndTravelAndZeroTimeRetainsIt() {
        ManualMechDrive drive = new ManualMechDrive();
        drive.advance(1f, 1f, 4f, 1f, 1f);
        var slow = drive.advance(1f, 1f, .5f, 1f, .1f);
        assertTrue(Math.hypot(drive.velocityX(), drive.velocityY()) <= .500001f);
        assertTrue(Math.hypot(slow.dx(), slow.dy()) <= .050001f);
        float velocity = drive.velocityX();
        assertEquals(new ManualMechDrive.Step(0f, 0f), drive.advance(-1f, 0f, 0f, 1f, 0f));
        assertEquals(velocity, drive.velocityX());
    }
}
