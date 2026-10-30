package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapon.BeamWander;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetPlaneAimTest {

    private static final float RADIUS = 0.3f;
    private static final float HALF_HEIGHT = 0.45f;

    @Test
    void onTargetSampleStaysInsideBothSilhouetteAxes() {
        TargetPlaneAim.Sample sample = TargetPlaneAim.sample(
                1f, 1f, 3f, RADIUS, HALF_HEIGHT,
                new QueueRandom(0f, 1f, 0f));

        assertTrue(sample.onTarget());
        assertTrue(Math.abs(sample.lateral()) < RADIUS);
        assertTrue(Math.abs(sample.elevation()) < HALF_HEIGHT);
    }

    @Test
    void lateralMissClearsTheHorizontalSilhouette() {
        TargetPlaneAim.Sample sample = TargetPlaneAim.sample(
                0f, 1f, 0f, RADIUS, HALF_HEIGHT,
                new QueueRandom(0.5f, 0f, 0f));

        assertFalse(sample.onTarget());
        assertTrue(sample.lateral() > RADIUS);
    }

    @Test
    void elevationMissCanFlyHighOrLow() {
        TargetPlaneAim.Sample high = TargetPlaneAim.sample(
                0f, 1f, 0f, RADIUS, HALF_HEIGHT,
                new QueueRandom(0.5f, 0.25f, 0f));
        TargetPlaneAim.Sample low = TargetPlaneAim.sample(
                0f, 1f, 0f, RADIUS, HALF_HEIGHT,
                new QueueRandom(0.5f, 0.75f, 0f));

        assertTrue(high.elevation() > HALF_HEIGHT);
        assertTrue(low.elevation() < -HALF_HEIGHT);
    }

    @Test
    void incomingAccuracyMultiplierParticipatesInTheSingleCommitRoll() {
        TargetPlaneAim.Sample sample = TargetPlaneAim.sample(
                1f, 0.5f, 0f, RADIUS, HALF_HEIGHT,
                new QueueRandom(0.6f, 0f, 0f));

        assertFalse(sample.onTarget());
    }

    /** A crawling beam hits exactly while its path's share is under the chance the roll would have used. */
    @Test
    void aCrawlHitsWhileTheShareIsUnderTheChance() {
        float cos = (float) Math.cos(0.7);
        float sin = (float) Math.sin(0.7);
        assertTrue(TargetPlaneAim.crawl(0.95f, 1f, 0.1f, 1.2f, 1.5f,
                new BeamWander.Sample(0.9499f, cos, sin)).onTarget());
        assertFalse(TargetPlaneAim.crawl(0.95f, 1f, 0.1f, 1.2f, 1.5f,
                new BeamWander.Sample(0.9501f, cos, sin)).onTarget());
        assertFalse(TargetPlaneAim.crawl(0.95f, 0.5f, 0.1f, 1.2f, 1.5f,
                new BeamWander.Sample(0.6f, cos, sin)).onTarget(), "the target's own evasion is in the chance");
    }

    /** Sliding off the edge moves the lance a short way, not from the centre out past the silhouette. */
    @Test
    void aCrawlSlidesOffTheEdgeWithoutJumping() {
        for (double angle = 0; angle < 2 * Math.PI; angle += 0.1) {
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            TargetPlaneAim.Sample in = TargetPlaneAim.crawl(0.95f, 1f, 0.1f, 1.2f, 1.5f,
                    new BeamWander.Sample(0.94999f, cos, sin));
            TargetPlaneAim.Sample out = TargetPlaneAim.crawl(0.95f, 1f, 0.1f, 1.2f, 1.5f,
                    new BeamWander.Sample(0.95001f, cos, sin));
            float step = (float) Math.hypot(out.lateral() - in.lateral(), out.elevation() - in.elevation());
            assertTrue(step < 0.01f, "step " + step + " at bearing " + angle);
        }
    }

    /** Over a hold the crawl misses by what the per-pulse rule misses by: past the silhouette, on average. */
    @Test
    void aCrawlKeepsThePerPulseMeanMiss() {
        float spread = 0.1f;
        int n = 2000;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            float share = 0.95f + 0.05f * (i + 0.5f) / n;
            TargetPlaneAim.Sample miss = TargetPlaneAim.crawl(0.95f, 1f, spread, 1.2f, 1.5f,
                    new BeamWander.Sample(share, 1f, 0f));
            sum += miss.lateral();
        }
        float expected = 1.2f + 0.5f * (TargetPlaneAim.MISS_CLEARANCE_MIN + TargetPlaneAim.MISS_CLEARANCE_MAX) + spread;
        assertEquals(expected, sum / n, 1e-3);
    }

    private static final class QueueRandom extends Random {
        private final ArrayDeque<Float> values = new ArrayDeque<>();

        QueueRandom(float... values) {
            for (float value : values) this.values.add(value);
        }

        @Override
        public float nextFloat() {
            if (values.isEmpty()) throw new IllegalStateException("QueueRandom exhausted");
            return values.remove();
        }
    }
}
