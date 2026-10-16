package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.cinematic.CinematicShot.LockOverlay;

/**
 * The planet-lock gesture over time: four arcs close on a limb, a leader runs out to a label, and
 * all of it clears together. Geometry is the composer's tracked centre and radius; the timing
 * curves are the ones {@code c1_overlay.py} reviewed, so the preview and the player agree.
 */
public final class LockTrack {
    /** How far outside the limb the arcs start closing from, as a fraction of the radius. */
    static final double RING_MARGIN = 1.045;
    static final double CLOSE_TRAVEL = 0.14;

    /** Everything is in source pixels (1920x1080) except the unit-less fades. */
    public record State(double centerX, double centerY, double ringRadius, double ringAlpha, double run,
                        double textAlpha, double slide, double fade) { }

    private final LockOverlay lock;

    public LockTrack(LockOverlay lock) { this.lock = lock; }

    public LockOverlay lock() { return lock; }

    /** The state at {@code t}, or null when the lock is not on screen. */
    public State at(double t) {
        double tLock = lock.in();
        if (t < tLock || t > lock.out() + lock.outSeconds() || lock.track().length == 0) return null;
        double[] p = sample(t);
        double fade = 1 - Ease.smooth((t - lock.out()) / lock.outSeconds());
        double closed = Ease.smooth((t - tLock) / lock.closeSeconds());
        double radius = p[3] * (RING_MARGIN + CLOSE_TRAVEL * (1 - closed));
        double flash = Math.exp(-Math.pow((t - tLock - lock.closeSeconds()) / 0.25, 2));
        double ringAlpha = (0.6 + 0.35 * flash) * Ease.smooth((t - tLock) / 0.35) * fade;
        double run = Ease.smooth((t - tLock - lock.closeSeconds() * 0.6) / 0.6);
        double textStart = tLock + lock.closeSeconds() * 1.2;
        double textAlpha = Ease.smooth((t - textStart) / 0.45) * fade;
        double slide = 10 * (1 - Ease.smooth((t - textStart) / 0.6));
        return new State(p[1], p[2], radius, ringAlpha, run, textAlpha, slide, fade);
    }

    /** Linear interpolation of the tracked samples, clamped at both ends: [t, x, y, r]. */
    double[] sample(double t) {
        double[][] track = lock.track();
        if (t <= track[0][0]) return track[0];
        for (int i = 1; i < track.length; i++) {
            if (t > track[i][0]) continue;
            double[] a = track[i - 1], b = track[i];
            double k = b[0] == a[0] ? 1 : (t - a[0]) / (b[0] - a[0]);
            return new double[] {t, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k, a[3] + (b[3] - a[3]) * k};
        }
        return track[track.length - 1];
    }
}
