package com.dillon.starsectormarines.battle.vehicle;

/**
 * A short, dense, kinematically-feasible pose sequence — the output of
 * {@link LocalTrajectoryPlanner}. Feasible by construction: every pose was
 * footprint-checked against the grid the planner ran on.
 *
 * <p>Slice-2's controller tracks one of these with {@link PurePursuit} (hence
 * the {@link #xs()} / {@link #ys()} accessors) and reads {@link #lengthCells()}
 * for the brake taper; {@link #sampleAtDistance} mirrors the interpolation the
 * old docking / playback code used, for any sampler that wants a pose a given
 * arc-distance along the path.
 *
 * <p>Immutable. A {@code null} return from the planner — not an empty
 * {@code Trajectory} — signals "no forward trajectory"; this type always holds
 * at least two poses.
 */
public final class Trajectory {

    private final float[] xs;
    private final float[] ys;
    private final float[] headings;
    /** Cumulative arc length to each vertex; {@code cum[0] == 0}. */
    private final float[] cum;
    private final float length;

    public Trajectory(float[] xs, float[] ys, float[] headings) {
        if (xs.length != ys.length || xs.length != headings.length || xs.length < 2) {
            throw new IllegalArgumentException("trajectory needs >= 2 matched poses");
        }
        this.xs = xs;
        this.ys = ys;
        this.headings = headings;
        this.cum = new float[xs.length];
        float acc = 0f;
        for (int i = 1; i < xs.length; i++) {
            float dx = xs[i] - xs[i - 1];
            float dy = ys[i] - ys[i - 1];
            acc += (float) Math.sqrt(dx * dx + dy * dy);
            cum[i] = acc;
        }
        this.length = acc;
    }

    public int size() { return xs.length; }
    public float lengthCells() { return length; }
    public float[] xs() { return xs; }
    public float[] ys() { return ys; }
    public float[] headings() { return headings; }

    public Pose pose(int i) { return new Pose(xs[i], ys[i], headings[i]); }
    public Pose start() { return pose(0); }
    public Pose end() { return pose(xs.length - 1); }

    /**
     * Pose at arc-distance {@code d} cells along the trajectory, clamped to the
     * endpoints. Position is linearly interpolated; heading is interpolated on
     * the shortest angular arc (matching the old playback / docking sampler).
     */
    public Pose sampleAtDistance(float d) {
        if (d <= 0f) return start();
        if (d >= length) return end();
        int i = 1;
        while (i < cum.length && cum[i] < d) i++;
        float segStart = cum[i - 1];
        float segLen = cum[i] - segStart;
        float t = (segLen > 1e-6f) ? (d - segStart) / segLen : 0f;
        float x = xs[i - 1] + (xs[i] - xs[i - 1]) * t;
        float y = ys[i - 1] + (ys[i] - ys[i - 1]) * t;
        float dh = ((headings[i] - headings[i - 1] + 540f) % 360f) - 180f;
        float h = headings[i - 1] + dh * t;
        return new Pose(x, y, h);
    }
}
