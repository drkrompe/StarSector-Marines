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
    void controlledAimWeighsAccuracyLessButKeepsAuthoredSpreadAtFullWeight() {
        float bound = (0.2f + 0.6f + 0.7f * 0.5f) / 20f;
        Random rng = new Random(7);
        for (int i = 0; i < 2000; i++) {
            assertTrue(magnitude(PointAim.sample(0.5f, 0.6f, 20f, PointAimDispersion.CONTROLLED, rng)) <= bound);
        }
        // Spread still separates weapons: a wide-spread SMG stays looser than a marksman rifle.
        assertTrue(meanMiss(0.5f, 1.7f, PointAimDispersion.CONTROLLED)
                > 2.5f * meanMiss(0.5f, 0.15f, PointAimDispersion.CONTROLLED));
    }

    @Test
    void controlledRookHitsAManAtHalfRangeAboutHalfTheTime() {
        // A regular Rook at weapon-range calibration: 0.28 accuracy after 42% falloff, 0.75 spread, 22 range.
        float accuracy = 0.28f * (1f - 0.42f);
        float controlled = hitRate(accuracy, PointAimDispersion.CONTROLLED);
        float weapon = hitRate(accuracy, PointAimDispersion.WEAPON);
        assertTrue(controlled >= 0.45f && controlled <= 0.70f, "controlled " + controlled);
        assertTrue(weapon < 0.10f, "weapon " + weapon);
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

    /** Share of rounds landing within a quarter cell of the cursor at 11 cells, half the Rook's range. */
    private static float hitRate(float accuracy, PointAimDispersion dispersion) {
        Random rng = new Random(11);
        int hits = 0;
        int samples = 4000;
        for (int i = 0; i < samples; i++) {
            if (magnitude(PointAim.sample(accuracy, 0.75f, 22f, dispersion, rng)) * 11f <= 0.25f) hits++;
        }
        return hits / (float) samples;
    }

    private static float meanMiss(float accuracy, float spread, PointAimDispersion dispersion) {
        Random rng = new Random(3);
        float total = 0f;
        for (int i = 0; i < 2000; i++) {
            total += magnitude(PointAim.sample(accuracy, spread, 20f, dispersion, rng));
        }
        return total / 2000f;
    }

    private static float magnitude(PointAim.Sample sample) {
        return (float) Math.hypot(sample.lateralSlope(), sample.elevationSlope());
    }
}
