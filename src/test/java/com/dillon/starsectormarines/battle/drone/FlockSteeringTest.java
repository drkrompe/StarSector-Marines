package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlockSteeringTest {

    private static final FlockWeights W = FlockWeights.WASP;
    private static final float DT = 0.05f;

    /** Members integrated with the velocity they ask for, toward an anchor that may move. */
    private static final class Toy {
        final int n;
        final long[] ids;
        final float[] xs, ys, vxs, vys;

        Toy(float[] xs, float[] ys) {
            n = xs.length;
            ids = new long[n];
            for (int i = 0; i < n; i++) ids[i] = 100 + i;
            this.xs = xs.clone();
            this.ys = ys.clone();
            vxs = new float[n];
            vys = new float[n];
        }

        void step(float ax, float ay) {
            float[][] next = new float[n][];
            for (int i = 0; i < n; i++) {
                float dx = ax - xs[i], dy = ay - ys[i];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float ux = d > 1e-4f ? dx / d : 0f, uy = d > 1e-4f ? dy / d : 0f;
                next[i] = FlockSteering.velocity(i, ids, xs, ys, vxs, vys, n, ux, uy, d, W);
            }
            for (int i = 0; i < n; i++) {
                vxs[i] = next[i][0];
                vys[i] = next[i][1];
                xs[i] += vxs[i] * DT;
                ys[i] += vys[i] * DT;
            }
        }

        float closestPair() {
            float best = Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) best = Math.min(best, (float) Math.hypot(xs[i] - xs[j], ys[i] - ys[j]));
            }
            return best;
        }

        float[] centre() {
            float sx = 0f, sy = 0f;
            for (int i = 0; i < n; i++) {
                sx += xs[i];
                sy += ys[i];
            }
            return new float[]{sx / n, sy / n};
        }
    }

    private static Toy onePoint(int n) {
        return new Toy(new float[n], new float[n]);
    }

    @Test
    void membersLaunchedOnOnePointSpreadApart() {
        Toy toy = onePoint(12);
        for (int t = 0; t < 40; t++) toy.step(0f, 0f);
        assertTrue(toy.closestPair() > 0.5f, "closest pair " + toy.closestPair());
    }

    @Test
    void aMemberTurnsToItsNeighboursHeading() {
        // Self flies north; three neighbours beside it fly east; no anchor pull.
        long[] ids = {1, 2, 3, 4};
        float[] xs = {0f, 3f, 0f, 3f};
        float[] ys = {0f, 0f, 3f, 3f};
        float[] vxs = {0f, 4f, 4f, 4f};
        float[] vys = {4f, 0f, 0f, 0f};
        float[] withMates = FlockSteering.velocity(0, ids, xs, ys, vxs, vys, 4, 0f, 0f, 0f, W);
        float[] alone = FlockSteering.velocity(0, ids, xs, ys, vxs, vys, 1, 0f, 0f, 0f, W);
        assertTrue(withMates[0] > 0f, "self is pulled east with its mates: " + withMates[0]);
        assertEquals(0f, alone[0], 1e-6f, "alone it has nothing to align with");
    }

    @Test
    void theFlockClosesOnAMovingAnchorAndKeepsUp() {
        float[] xs = new float[12], ys = new float[12];
        for (int i = 0; i < 12; i++) {
            xs[i] = -20f + (i % 4) * 1.5f;
            ys[i] = (i / 4) * 1.5f;
        }
        Toy toy = new Toy(xs, ys);
        float ax = 0f;
        for (int t = 0; t < 200; t++) {
            ax += 3f * DT;
            toy.step(ax, 0f);
        }
        float[] c = toy.centre();
        assertTrue(Math.hypot(c[0] - ax, c[1]) < 4f, "centre " + c[0] + "," + c[1] + " vs anchor " + ax);
    }

    @Test
    void membersNeverStackWhileHoldingOnTheAnchor() {
        float[] xs = new float[16], ys = new float[16];
        for (int i = 0; i < 16; i++) {
            xs[i] = (float) Math.cos(i) * 10f;
            ys[i] = (float) Math.sin(i) * 10f;
        }
        Toy toy = new Toy(xs, ys);
        float closest = Float.MAX_VALUE;
        for (int t = 0; t < 300; t++) {
            toy.step(0f, 0f);
            if (t > 60) closest = Math.min(closest, toy.closestPair());
        }
        assertTrue(closest > 0.4f, "closest pair while holding " + closest);
        float[] c = toy.centre();
        assertTrue(Math.hypot(c[0], c[1]) < 2f, "the flock holds round its anchor");
    }

    @Test
    void theAskedForSpeedNeverExceedsTheFlocksMaximum() {
        long[] ids = {1, 2};
        float[] v = FlockSteering.velocity(0, ids, new float[]{0f, 0.1f}, new float[]{0f, 0f},
                new float[]{50f, 50f}, new float[]{0f, 0f}, 2, 1f, 0f, 100f, W);
        assertTrue(Math.hypot(v[0], v[1]) <= W.maxSpeed() + 1e-4f);
    }
}
