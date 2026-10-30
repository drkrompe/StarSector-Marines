package com.dillon.starsectormarines.battle.weapon;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeamWanderTest {

    private static final float PULSE = 1f / 15f;
    private static final int PULSES = 20_000;

    @Test
    void theSameSeedLaysTheSamePath() {
        BeamWander a = BeamWander.seeded(0.5f, 1f, 42L);
        BeamWander b = BeamWander.seeded(0.5f, 1f, 42L);
        for (int i = 0; i < 300; i++) {
            assertEquals(a.sample(null), b.sample(null));
            a.advance(PULSE);
            b.advance(PULSE);
        }
    }

    @Test
    void theFirstPulseDrawsTheSeedAndNothingAfter() {
        Random rng = new Random(7L);
        BeamWander wander = new BeamWander(0.5f, 1f);
        for (int i = 0; i < 50; i++) {
            wander.sample(rng);
            wander.advance(PULSE);
        }
        Random control = new Random(7L);
        long seed = control.nextLong();
        assertEquals(control.nextLong(), rng.nextLong(), "one draw from the battle stream, on the first pulse");
        BeamWander replay = BeamWander.seeded(0.5f, 1f, seed);
        for (int i = 0; i < 50; i++) replay.advance(PULSE);
        assertEquals(replay.sample(null), wander.sample(null));
    }

    /**
     * Consecutive pulses land next to each other: no step exceeds the path's speed limit, and the typical step is a
     * small fraction of the disk, where a fresh draw per pulse moves 0.9 of its radius on average.
     */
    @Test
    void consecutivePulsesCrawlRatherThanJump() {
        float period = 1f;
        BeamWander wander = BeamWander.seeded(period, 1f, 3L);
        BeamWander.Sample last = wander.sample(null);
        double maxStep = 0;
        double sum = 0;
        for (int i = 0; i < PULSES; i++) {
            wander.advance(PULSE);
            BeamWander.Sample now = wander.sample(null);
            double step = Math.hypot(now.diskX() - last.diskX(), now.diskY() - last.diskY());
            maxStep = Math.max(maxStep, step);
            sum += step;
            last = now;
        }
        double limit = BeamWander.MAX_DISK_STEP_PER_PERIOD * PULSE / period;
        assertTrue(maxStep <= limit, "largest per-pulse step " + maxStep + " against the limit " + limit);
        assertTrue(sum / PULSES < 0.1, "mean per-pulse step " + sum / PULSES + " of the unit disk");
    }

    @Test
    void theShareStaysInsideTheDiskAndSpendsTheBudgetUniformly() {
        BeamWander wander = BeamWander.seeded(0.5f, 1f, 11L);
        double sum = 0;
        int under = 0;
        for (int i = 0; i < PULSES * 4; i++) {
            BeamWander.Sample s = wander.sample(null);
            assertTrue(s.share() >= 0f && s.share() < 1f);
            sum += s.share();
            if (s.share() < 0.95f) under++;
            wander.advance(PULSE);
        }
        int n = PULSES * 4;
        assertEquals(0.5, sum / n, 0.03, "mean share of a uniform budget");
        assertEquals(0.95, under / (double) n, 0.02, "a 0.95 hit chance holds over a long hold");
    }
}
