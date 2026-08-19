package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Random;

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
