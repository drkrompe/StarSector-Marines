package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * A hull's outline, normalized so it can be stretched onto any deck size.
 *
 * <p>Samples run bow to stern. At each sample the port and starboard values are
 * the fraction of the deck's maximum half-beam the hull occupies on that side,
 * in {@code [0, 1]}. Storing the two sides separately is what preserves a real
 * hull's asymmetry — a synthetic taper can only ever produce something
 * mirrored, which is precisely the station geometry this family exists to avoid.
 *
 * <p>{@link #aspect()} keeps the hull's drawn proportions, which is what lets a
 * deck be sized from the rooms it must contain while still coming out the shape
 * of the actual ship.
 *
 * <p>Deliberately free of any notion of where the outline came from. A silhouette
 * traced from ship art, authored by hand, or generated all satisfy the same
 * contract, and nothing here reads a file.
 */
public final class HullSilhouette {

    private final float[] port;
    private final float[] starboard;
    private final float aspect;
    private final String source;

    /**
     * @param port per-sample fraction of maximum half-beam on the port side
     * @param starboard the same to starboard; must match {@code port} in length
     * @param aspect the hull's beam divided by its length, as drawn
     * @param source a human-readable origin, used in evidence and error messages
     */
    public HullSilhouette(float[] port, float[] starboard, float aspect, String source) {
        if (port.length != starboard.length) {
            throw new IllegalArgumentException("silhouette sides must have equal sample counts");
        }
        if (port.length < 2) {
            throw new IllegalArgumentException("a silhouette needs at least two samples");
        }
        if (!(aspect > 0f)) {
            throw new IllegalArgumentException("hull aspect must be positive");
        }
        this.port = clamped(port);
        this.starboard = clamped(starboard);
        this.aspect = aspect;
        this.source = source;
    }

    /** Where this outline came from — a hull id, or a description of the generator. */
    public String source() {
        return source;
    }

    /** The hull's beam divided by its length, as drawn. Below 1 for anything ship-shaped. */
    public float aspect() {
        return aspect;
    }

    public int samples() {
        return port.length;
    }

    /** Port-side occupancy at normalized position {@code t} along the hull, bow at 0. */
    public float portAt(float t) {
        return sample(port, t);
    }

    /** Starboard-side occupancy at normalized position {@code t} along the hull, bow at 0. */
    public float starboardAt(float t) {
        return sample(starboard, t);
    }

    /** Linearly interpolated so a short hull stretches smoothly onto a long deck. */
    private float sample(float[] values, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        float scaled = clamped * (values.length - 1);
        int index = (int) Math.floor(scaled);
        if (index >= values.length - 1) return values[values.length - 1];
        float frac = scaled - index;
        return values[index] * (1f - frac) + values[index + 1] * frac;
    }

    private static float[] clamped(float[] values) {
        float[] copy = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            copy[i] = Math.max(0f, Math.min(1f, values[i]));
        }
        return copy;
    }
}
