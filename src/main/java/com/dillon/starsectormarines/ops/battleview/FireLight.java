package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;

import java.util.Arrays;

/**
 * Burning ground as a few pooled lights (`fire-nouns.md`, Look), not one per
 * cell: burning cells are gathered into {@link #BIN}-cell squares, each square
 * with fire in it is one light at its intensity-weighted centre, sized by how
 * much of it burns and as bright as it burns hot. Every such square is kept:
 * which of them to draw is the reader's choice, by what is in view. The squares are regathered when the
 * fire changes; each light flickers on the animation clock, at its own phase,
 * so a still frame is a pure function of the fire and the clock.
 *
 * <p>Both of fire's lights read it: the night shade, where a burn carves a warm
 * pool out of the dark ({@link NightLightingRenderSystem}), and the ground
 * relief's event lights ({@link GroundLightService}).
 */
final class FireLight {

    /** Side of a gathering square, in cells. */
    static final int BIN = 6;
    /** Fire's colour: a deep orange, warmer than a lamp. */
    static final float RED = 1f, GREEN = 0.5f, BLUE = 0.2f;

    private float[] x = new float[64], y = new float[64];
    private float[] radius = new float[64], strength = new float[64];
    private float[] phase = new float[64];
    private int count;
    private FireField builtFrom;
    private long builtRevision = -1;
    private float[] binSum = new float[0], binX = new float[0], binY = new float[0];
    private int[] binCells = new int[0];

    /** Regathers the squares when the fire has changed since the last call. */
    void update(FireField field) {
        if (field == null || field.burningCount() == 0) {
            count = 0;
            builtFrom = field;
            builtRevision = field == null ? -1 : field.revision();
            return;
        }
        if (field == builtFrom && field.revision() == builtRevision) return;
        builtFrom = field;
        builtRevision = field.revision();
        int bw = (field.width() + BIN - 1) / BIN, bh = (field.height() + BIN - 1) / BIN;
        if (binSum.length != bw * bh) {
            binSum = new float[bw * bh];
            binX = new float[bw * bh];
            binY = new float[bw * bh];
            binCells = new int[bw * bh];
        } else {
            Arrays.fill(binSum, 0f);
            Arrays.fill(binX, 0f);
            Arrays.fill(binY, 0f);
            Arrays.fill(binCells, 0);
        }
        int w = field.width();
        for (int i = 0, n = field.burningCount(); i < n; i++) {
            int idx = field.burningCell(i);
            int cx = idx % w, cy = idx / w;
            int b = (cy / BIN) * bw + cx / BIN;
            float heat = Math.max(1, field.intensityAt(idx)) / 255f;
            binSum[b] += heat;
            binX[b] += (cx + 0.5f) * heat;
            binY[b] += (cy + 0.5f) * heat;
            binCells[b]++;
        }
        count = 0;
        for (int b = 0; b < binSum.length; b++) {
            if (binCells[b] == 0) continue;
            float s = Math.min(1f, binSum[b] / (BIN * BIN * 0.3f));
            if (count == x.length) grow();
            int slot = count++;
            x[slot] = binX[b] / binSum[b];
            y[slot] = binY[b] / binSum[b];
            radius[slot] = 3f + 1.4f * (float) Math.sqrt(binCells[b]);
            strength[slot] = 0.35f + 0.65f * s;
            phase[slot] = (b * 0.6180339f) % 1f * 6.2831855f;
        }
    }

    private void grow() {
        int n = x.length * 2;
        x = Arrays.copyOf(x, n);
        y = Arrays.copyOf(y, n);
        radius = Arrays.copyOf(radius, n);
        strength = Arrays.copyOf(strength, n);
        phase = Arrays.copyOf(phase, n);
    }

    /** A host's own clock when it keeps one (a review pins it), else the wall clock, as the markers' pulse does. */
    static float clock(float animationTimeSeconds) {
        return Float.isFinite(animationTimeSeconds) ? animationTimeSeconds
                : (float) (System.currentTimeMillis() % 3_600_000L / 1000.0);
    }

    int count() { return count; }
    float x(int i) { return x[i]; }
    float y(int i) { return y[i]; }
    float radius(int i) { return radius[i]; }
    /** The gathered strength, before flicker: what a reader ranks lights by. */
    float baseStrength(int i) { return strength[i]; }

    /** The light's strength at an animation time, flickering about its gathered value. */
    float strength(int i, float timeSeconds) {
        float p = phase[i];
        float flicker = 0.88f + 0.08f * (float) Math.sin(timeSeconds * 7.3f + p)
                + 0.04f * (float) Math.sin(timeSeconds * 17.9f + p * 2.3f);
        return strength[i] * flicker;
    }

    /** A light's pool at a point: the lamps' own falloff, squared toward the edge. */
    float pool(int i, float px, float py, float timeSeconds) {
        return pool(i, px, py, timeSeconds, strength(i, timeSeconds));
    }

    /** {@link #pool} with the light's flickered strength already read, for a reader filling many points of one light. */
    float pool(int i, float px, float py, float timeSeconds, float strengthNow) {
        float dx = px - x[i], dy = py - y[i], r = radius[i];
        float falloff = Math.max(0f, 1f - (dx * dx + dy * dy) / (r * r));
        return falloff * falloff * strengthNow;
    }
}
