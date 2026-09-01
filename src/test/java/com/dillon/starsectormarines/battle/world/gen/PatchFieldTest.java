package com.dillon.starsectormarines.battle.world.gen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two properties {@link PatchField} exists to hold at once, and which pull
 * against each other: neighbours agree, and the values are still uniform.
 *
 * <p>Either alone is trivial — a constant field is perfectly coherent, and an
 * independent draw per cell is perfectly uniform. What is hard is both, and the
 * failure is silent in the direction that matters: a smooth field built by
 * interpolating between hashed corners passes every coherence measure and is
 * not uniform, because averaging independent values starves the ends of the
 * range. Substituted for a weighted pool's roll, that quietly rewrites what the
 * ground is made of. An earlier draft of this class did exactly that and took a
 * 5%-weighted sand down to 0.9% while looking correct.
 */
class PatchFieldTest {

    private static final int SPAN = 400;

    /**
     * Uniform to within a percentage point per decile. Stated as deciles rather
     * than as a mean, because the fault this guards against is in the tails and
     * a mean is blind to it: pulling both ends toward the middle leaves the
     * average exactly where it was.
     */
    @Test
    void everyPartOfTheRangeIsEquallyLikely() {
        int[] deciles = new int[10];
        PatchField field = new PatchField(9001L, 6f);
        for (int y = 0; y < SPAN; y++) {
            for (int x = 0; x < SPAN; x++) {
                float v = field.sample(x, y);
                assertTrue(v >= 0f && v < 1f, "sample out of range: " + v);
                deciles[(int) (v * 10)]++;
            }
        }
        int expected = SPAN * SPAN / 10;
        for (int i = 0; i < deciles.length; i++) {
            double off = Math.abs(deciles[i] - expected) / (double) expected;
            assertTrue(off < 0.12, "decile " + i + " holds " + deciles[i] + " of "
                    + (SPAN * SPAN) + " samples against " + expected + " expected — the field is "
                    + "not uniform, so it decides the mix of whatever pool it is rolled against "
                    + "and not merely its arrangement");
        }
    }

    /**
     * Neighbours usually agree. The comparison is against the same field with
     * its patches shrunk below a cell, which is the incoherent case written in
     * the same terms rather than a number picked to pass.
     */
    @Test
    void neighboursUsuallyShareAPatch() {
        assertTrue(neighbourAgreement(new PatchField(9001L, 6f)) > 0.75,
                "cells do not share a patch with their neighbours often enough to read as one");
        assertTrue(neighbourAgreement(new PatchField(9001L, 0.2f)) < 0.05,
                "the control is not incoherent, so the measure above proves nothing");
    }

    /** Share of horizontally adjacent cell pairs holding the same value. */
    private static double neighbourAgreement(PatchField field) {
        int same = 0;
        int pairs = 0;
        for (int y = 0; y < SPAN; y++) {
            for (int x = 0; x + 1 < SPAN; x++) {
                if (field.sample(x, y) == field.sample(x + 1, y)) same++;
                pairs++;
            }
        }
        return same / (double) pairs;
    }

    /**
     * A pure function of (seed, x, y) — no {@link java.util.Random}, so two
     * readers agree without being ordered relative to each other and a region
     * samples the same whichever cell is visited first.
     */
    @Test
    void theSameCellAlwaysSamplesTheSame() {
        PatchField one = new PatchField(4242L, 6f);
        PatchField same = new PatchField(4242L, 6f);
        PatchField other = new PatchField(4243L, 6f);
        int differed = 0;
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 40; x++) {
                assertEquals(one.sample(x, y), same.sample(x, y),
                        "same seed and feature size gave different fields at " + x + "," + y);
                assertEquals(one.sample(x, y), one.sample(x, y), "sampling is not repeatable");
                if (one.sample(x, y) != other.sample(x, y)) differed++;
            }
        }
        assertTrue(differed > 40 * 40 / 2,
                "a different seed gave nearly the same field, so the seed is barely mixed in");
    }

    @Test
    void aFeatureSizeMustBeAFeature() {
        assertThrows(IllegalArgumentException.class, () -> new PatchField(1L, 0f));
        assertThrows(IllegalArgumentException.class, () -> new PatchField(1L, -3f));
    }
}
