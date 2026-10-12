package com.dillon.starsectormarines.battle.warship;

import java.util.Arrays;

/**
 * Where a swarm's bodies come down and when: a random scatter over a disc with
 * a staggered arrival, so a volley reads as a rolling burst rather than a tidy
 * tulip.
 *
 * <p>The points are a stratified random scatter: each body is given a ring of
 * the disc (so cover is even enough that no hole is left and no clump forms)
 * but its place in the ring is random, and the rings are dealt out at random.
 * The arrival order blends distance from the shooter, so the burst can walk
 * across the ground, with a share of pure chance so it is never a metronome.
 * Everything is a pure function of the seed: nothing draws from a shared stream.
 */
public final class SwarmLayout {

    private static final int STREAM = 400;
    private static final float GOLDEN_ANGLE = 2.3999632f;

    private SwarmLayout() {}

    /**
     * A sheaf: {@code n} aim points in arrival order, and the arrival time of
     * each in seconds (zero at the first, rising to the last).
     */
    public record Sheaf(float[] aimX, float[] aimY, float[] arrivalSeconds) {
        public int size() {
            return aimX.length;
        }
    }

    /**
     * {@code n} aim points scattered over the disc of {@code radius} about
     * {@code (cx, cy)}, ordered by arrival, with arrivals spread evenly (and
     * jittered) between {@code minSeconds} and {@code maxSeconds}.
     *
     * @param ripple how far arrivals follow distance from {@code (fromX, fromY)}: 1
     *               lands the nearest first, 0 lands in no order at all
     */
    public static Sheaf sheaf(long seed, int n, float cx, float cy, float radius,
                              float fromX, float fromY, float ripple, float minSeconds, float maxSeconds) {
        float[] px = new float[n];
        float[] py = new float[n];
        // Stratified radii dealt out in a random order: even cover, random placement.
        int[] ring = deal(seed, n, STREAM + 5 * n + 4);
        float rotation = MissilePath.unit(seed, STREAM) * 2f * (float) Math.PI;
        for (int i = 0; i < n; i++) {
            float r = radius * (float) Math.sqrt((ring[i] + MissilePath.unit(seed, STREAM + 1 + 3 * i)) / n);
            float a = rotation + i * GOLDEN_ANGLE + (MissilePath.unit(seed, STREAM + 2 + 3 * i) - 0.5f) * 3.2f;
            px[i] = cx + (float) Math.cos(a) * r;
            py[i] = cy + (float) Math.sin(a) * r;
        }
        // The order they land in: distance from the shooter, blended with chance.
        float farthest = 0.001f;
        float[] dist = new float[n];
        for (int i = 0; i < n; i++) {
            dist[i] = (float) Math.hypot(px[i] - fromX, py[i] - fromY);
            farthest = Math.max(farthest, dist[i]);
        }
        Integer[] order = new Integer[n];
        float[] key = new float[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
            key[i] = ripple * (dist[i] / farthest) + (1f - ripple) * MissilePath.unit(seed, STREAM + 3 * n + 2 + i);
        }
        Arrays.sort(order, (a, b) -> Float.compare(key[a], key[b]));
        float[] ax = new float[n];
        float[] ay = new float[n];
        float[] at = new float[n];
        float span = Math.max(0f, maxSeconds - minSeconds);
        for (int rank = 0; rank < n; rank++) {
            int i = order[rank];
            ax[rank] = px[i];
            ay[rank] = py[i];
            float slot = (rank + 0.5f + (MissilePath.unit(seed, STREAM + 4 * n + 3 + rank) - 0.5f) * 0.9f) / n;
            at[rank] = minSeconds + span * Math.max(0f, Math.min(1f, slot));
        }
        return new Sheaf(ax, ay, at);
    }

    /** A random permutation of {@code 0..n-1}, from the seed (Fisher-Yates over the hash). */
    public static int[] deal(long seed, int n, int stream) {
        int[] deck = new int[n];
        for (int i = 0; i < n; i++) deck[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = Math.min(i, (int) (MissilePath.unit(seed, stream + i) * (i + 1)));
            int t = deck[i];
            deck[i] = deck[j];
            deck[j] = t;
        }
        return deck;
    }
}
