package com.dillon.starsectormarines.battle.vehicle;

/**
 * How an articulated hull is cut into segments along its length ({@link VehicleType#articulation()}): a drive cab
 * and the modules trailing it, each joined to the one ahead by a hinge. All figures are cells along the hull's straight
 * frame, +Y forward from the hull's centre, as the sheet paints it. The simulation never reads this: the hull moves as
 * one rigid {@link BicycleBody}, and the bend is drawn ({@link TrailerKinematic}).
 *
 * @param hingeY each hinge's position, nose to tail: hinge {@code i} joins segment {@code i} to segment {@code i + 1}
 * @param axleY  each segment's axle centre, nose to tail, the point a trailing segment drags along behind its hinge
 *               (the mid point between its axles); the cab's is unused
 * @param gap    the length of each hinge section, the pistons, cables and pivot drum between two plates, centred on its
 *               hinge: drawn stretched across the outside of a bend and pressed together on the inside, never turned
 *               rigid with either plate
 */
public record Articulation(float[] hingeY, float[] axleY, float gap) {

    /** How far a trailing segment may swing off the one ahead of it, degrees: where its hinge pistons bottom out. */
    public static final float MAX_BEND_DEG = 38f;

    /** A hull whose plates meet at their hinges, with no section between them. */
    public Articulation(float[] hingeY, float[] axleY) {
        this(hingeY, axleY, 0f);
    }

    public Articulation {
        if (!(gap >= 0f)) throw new IllegalArgumentException("a hinge section has no negative length");
        if (axleY.length != hingeY.length + 1) {
            throw new IllegalArgumentException("one axle per segment, one hinge between each pair");
        }
        for (int i = 0; i < hingeY.length; i++) {
            if (!(axleY[i + 1] < hingeY[i])) throw new IllegalArgumentException("a trailer's axle lies behind its hinge");
            if (i > 0 && !(hingeY[i] < hingeY[i - 1])) throw new IllegalArgumentException("hinges run nose to tail");
        }
    }

    public int segments() {
        return axleY.length;
    }

    /** The trailing segment {@code s}'s drag length, its front hinge to its axle centre, cells. */
    public float trailerLength(int s) {
        return hingeY[s - 1] - axleY[s];
    }

    /** Where hinge {@code h}'s section meets the plate ahead of it, cells on the straight hull. */
    public float gapFrontY(int h) {
        return hingeY[h] + gap / 2f;
    }

    /** Where hinge {@code h}'s section meets the plate behind it, cells on the straight hull. */
    public float gapRearY(int h) {
        return hingeY[h] - gap / 2f;
    }

    /** The segment a point at {@code y} on the straight hull belongs to: the cab ahead of the first hinge, and so on. */
    public int segmentOf(float y) {
        int s = 0;
        while (s < hingeY.length && y < hingeY[s]) s++;
        return s;
    }

    /** Sheet frame of segment {@code s}'s live layer: four hull frames, then a row of live segments. */
    public int liveFrame(int s) {
        return 4 + s;
    }

    /** Sheet frame of segment {@code s}'s wreck layer: after the live ones in the same row, or a row of its own for three. */
    public int wreckFrame(int s) {
        return segments() <= 2 ? 6 + s : 8 + s;
    }

    /** Rows of four frames the sheet holds: the hull row, then the segment rows. */
    public int sheetRows() {
        return segments() <= 2 ? 2 : 3;
    }
}
