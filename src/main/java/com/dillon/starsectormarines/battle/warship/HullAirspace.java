package com.dillon.starsectormarines.battle.warship;

/**
 * The ground a battle's hull fight is kept over: a rectangle of cells inside which ships fighting hulls choose their
 * stations. A mission declares it ({@link WarshipSystem#keepHullFightOver}) to keep a fight between ships off its
 * ground battle, in sight of it and not over the company's buildings; with none declared the whole map is the
 * airspace.
 *
 * <p>It shapes where a ship chooses to stand, not where it may fly: a hull entering, climbing out, on a route or under
 * a pilot crosses it freely, and a ship standing outside it flies in to its next station. Ships are kept clear of its
 * edges by their own half-length, as they are kept clear of the map's.
 *
 * @param minX west edge, in cells
 * @param minY south edge, in cells
 * @param maxX east edge, in cells
 * @param maxY north edge, in cells
 */
public record HullAirspace(float minX, float minY, float maxX, float maxY) {

    public HullAirspace {
        if (!(maxX > minX) || !(maxY > minY)) {
            throw new IllegalArgumentException("an airspace needs width and height: " + minX + ".." + maxX + " x "
                    + minY + ".." + maxY);
        }
    }

    /** The whole of a {@code width} by {@code height} map. */
    public static HullAirspace wholeMap(float width, float height) {
        return new HullAirspace(0f, 0f, width, height);
    }

    /** This airspace cut down to a {@code width} by {@code height} map, or the whole map if they do not meet. */
    public HullAirspace within(float width, float height) {
        float x0 = Math.max(0f, minX);
        float y0 = Math.max(0f, minY);
        float x1 = Math.min(width, maxX);
        float y1 = Math.min(height, maxY);
        return x1 > x0 && y1 > y0 ? new HullAirspace(x0, y0, x1, y1) : wholeMap(width, height);
    }

    /**
     * {@code (x, y)} brought inside the airspace with {@code margin} cells to spare at every edge; an airspace too
     * narrow for the margin puts the point on its middle line.
     *
     * @return {@code [x, y]}
     */
    public float[] clamp(float x, float y, float margin) {
        return new float[]{clampAxis(x, minX, maxX, margin), clampAxis(y, minY, maxY, margin)};
    }

    /** Whether {@code (x, y)} lies inside the airspace. */
    public boolean contains(float x, float y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    private static float clampAxis(float v, float lo, float hi, float margin) {
        if (hi - lo <= 2f * margin) return 0.5f * (lo + hi);
        return Math.max(lo + margin, Math.min(hi - margin, v));
    }
}
