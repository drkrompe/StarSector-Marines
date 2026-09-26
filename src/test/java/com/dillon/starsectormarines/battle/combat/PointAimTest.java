package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointAimTest {

    @Test
    void accuracyAndAuthoredSpreadBothWidenTheSameSample() {
        PointAim.Sample accurate = PointAim.sample(1f, 0f, 20f, new Random(19));
        PointAim.Sample moving = PointAim.sample(0.5f, 0f, 20f, new Random(19));
        PointAim.Sample spread = PointAim.sample(1f, 1f, 20f, new Random(19));

        assertTrue(magnitude(moving) > magnitude(accurate));
        assertTrue(magnitude(spread) > magnitude(accurate));
        assertEquals(magnitude(moving), magnitude(spread), 1e-6f);
    }

    @Test
    void errorDiskHasNoPrivilegedTargetSizeAndScalesAtWeaponRange() {
        PointAim.Sample shortRange = PointAim.sample(0.7f, 0.6f, 10f, new Random(42));
        PointAim.Sample longRange = PointAim.sample(0.7f, 0.6f, 20f, new Random(42));

        assertEquals(shortRange.lateralSlope(), longRange.lateralSlope() * 2f, 1e-6f);
        assertEquals(shortRange.elevationSlope(), longRange.elevationSlope() * 2f, 1e-6f);
        assertTrue(magnitude(shortRange) <= (0.2f + 0.6f + 2f * 0.3f) / 10f);
    }

    @Test
    void invalidNumericInputsCannotCreateANanTrajectory() {
        assertThrows(IllegalArgumentException.class,
                () -> PointAim.sample(Float.NaN, 0f, 20f, new Random(1)));
        assertThrows(IllegalArgumentException.class,
                () -> PointAim.sample(1f, Float.POSITIVE_INFINITY, 20f, new Random(1)));
        assertThrows(IllegalArgumentException.class,
                () -> PointAim.sample(1f, 0f, 0f, new Random(1)));
    }

    private static float magnitude(PointAim.Sample sample) {
        return (float) Math.hypot(sample.lateralSlope(), sample.elevationSlope());
    }
}
