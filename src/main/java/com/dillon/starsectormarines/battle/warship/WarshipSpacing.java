package com.dillon.starsectormarines.battle.warship;

/**
 * Open air between hulls: how far apart two hulls' footprints are, as a pure function of where they stand.
 *
 * <p>A hull's footprint is its sprite rectangle about its sprite centre ({@link Warship#distanceTo}), turned with its
 * heading: length along the nose, width across it. The gap between two footprints is read by separating axes: the
 * largest separation of the two rectangles' projections onto either hull's own axes. It is positive when they are
 * apart (a lower bound on the true distance, exact when two faces look at each other), and negative when they
 * overlap, by the shallowest depth either would have to move to clear the other.
 */
public final class WarshipSpacing {

    private WarshipSpacing() {}

    /** The gap, in cells, between the footprints of {@code a} and {@code b}; negative when they overlap. */
    public static float gap(Warship a, Warship b) {
        return gap(a.centerX(), a.centerY(), a.facingDegrees, a.lengthCells, a.widthCells,
                b.centerX(), b.centerY(), b.facingDegrees, b.lengthCells, b.widthCells);
    }

    /**
     * The separating-axis gap between two hull rectangles, each given by its centre, its facing (the hull convention,
     * nose along {@code (-sin, cos)}), its length and its width.
     */
    public static float gap(float ax, float ay, float aFacing, float aLength, float aWidth,
                            float bx, float by, float bFacing, float bLength, float bWidth) {
        double ra = Math.toRadians(aFacing);
        double rb = Math.toRadians(bFacing);
        float[] axes = {
                (float) -Math.sin(ra), (float) Math.cos(ra),
                (float) Math.cos(ra), (float) Math.sin(ra),
                (float) -Math.sin(rb), (float) Math.cos(rb),
                (float) Math.cos(rb), (float) Math.sin(rb)};
        float dx = bx - ax;
        float dy = by - ay;
        float best = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float nx = axes[2 * i];
            float ny = axes[2 * i + 1];
            float d = Math.abs(dx * nx + dy * ny);
            float sep = d - extent(axes[0], axes[1], axes[2], axes[3], aLength, aWidth, nx, ny)
                    - extent(axes[4], axes[5], axes[6], axes[7], bLength, bWidth, nx, ny);
            best = Math.max(best, sep);
        }
        return best;
    }

    /** Half the projection of a rectangle (nose axis {@code (lx, ly)}, beam axis {@code (wx, wy)}) onto {@code n}. */
    private static float extent(float lx, float ly, float wx, float wy, float length, float width, float nx, float ny) {
        return 0.5f * length * Math.abs(lx * nx + ly * ny) + 0.5f * width * Math.abs(wx * nx + wy * ny);
    }
}
