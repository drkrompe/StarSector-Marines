package com.dillon.starsectormarines.battle.decision;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the ordering primitives the pruned searches stop on.
 *
 * <p>A search that abandons candidates once their score floor loses to the
 * incumbent is only correct while it really is visiting them cheapest-first.
 * The fall-back search's floor can be negative — cover, friendly control and
 * the away-from-threat bonus all subtract — so ordering it means ordering
 * signed floats through an integer key, which is exactly the kind of bit
 * arithmetic that fails silently on one sign and never on the other.
 */
class CandidateOrderingTest {

    @Test
    void sortableBitsOrderNegativeAndPositiveScoresTogether() {
        float[] scores = {
                -1e9f, -1234.5f, -100f, -13.5f, -1f, -Float.MIN_VALUE,
                0f, Float.MIN_VALUE, 1f, 13.5f, 100f, 1234.5f, 1e9f,
                Float.MAX_VALUE,
        };
        for (int i = 1; i < scores.length; i++) {
            assertTrue(TacticalScoring.sortableFloatBits(scores[i - 1])
                            < TacticalScoring.sortableFloatBits(scores[i]),
                    "ordering broke between " + scores[i - 1] + " and " + scores[i]);
        }
    }

    @Test
    void sortableBitsAgreeWithFloatComparisonOnRandomPairs() {
        Random random = new Random(20260829L);
        for (int i = 0; i < 20_000; i++) {
            float a = randomScore(random);
            float b = randomScore(random);
            assertEquals(Integer.signum(Float.compare(a, b)),
                    Integer.signum(Integer.compare(
                            TacticalScoring.sortableFloatBits(a),
                            TacticalScoring.sortableFloatBits(b))),
                    "disagreed on " + a + " vs " + b);
        }
    }

    @Test
    void heapDrainsCandidatesCheapestFirst() {
        Random random = new Random(20260830L);
        for (int trial = 0; trial < 200; trial++) {
            int count = 1 + random.nextInt(400);
            float[] scores = new float[count];
            long[] heap = new long[count];
            for (int slot = 0; slot < count; slot++) {
                scores[slot] = randomScore(random);
                heap[slot] = ((long) TacticalScoring.sortableFloatBits(scores[slot]) << 32)
                        | slot;
            }
            TacticalScoring.heapify(heap, count);

            float[] drained = new float[count];
            for (int remaining = count, i = 0; remaining > 0; remaining--, i++) {
                long entry = heap[0];
                heap[0] = heap[remaining - 1];
                TacticalScoring.siftDown(heap, 0, remaining - 1);
                drained[i] = scores[(int) (entry & 0xFFFFFFFFL)];
            }

            float[] expected = Arrays.copyOf(scores, count);
            Arrays.sort(expected);
            assertArrayEqualsExactly(expected, drained, trial);
        }
    }

    /** Scores span both signs and several magnitudes, like the fall-back score does. */
    private static float randomScore(Random random) {
        int shape = random.nextInt(4);
        if (shape == 0) return 0f;
        float magnitude = random.nextFloat() * (float) Math.pow(10, random.nextInt(6));
        return random.nextBoolean() ? magnitude : -magnitude;
    }

    private static void assertArrayEqualsExactly(float[] expected, float[] actual, int trial) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i],
                    "heap drained out of order at " + i + " in trial " + trial);
        }
    }
}
