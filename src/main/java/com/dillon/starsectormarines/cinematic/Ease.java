package com.dillon.starsectormarines.cinematic;

/** Smoothstep easing shared by the overlay tracks; the composers' {@code smooth()}. */
final class Ease {
    private Ease() { }

    static double smooth(double x) {
        double c = x < 0 ? 0 : x > 1 ? 1 : x;
        return c * c * (3 - 2 * c);
    }

    /** Fades in over {@code fadeIn} from {@code t0} and out over {@code fadeOut} up to {@code t1}. */
    static double window(double t, double t0, double t1, double fadeIn, double fadeOut) {
        return smooth((t - t0) / fadeIn) * (1 - smooth((t - (t1 - fadeOut)) / fadeOut));
    }
}
