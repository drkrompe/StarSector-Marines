package com.dillon.starsectormarines.battle.command;

/**
 * Where a {@link PlaceDirective} points (`garrisonables.md`, place directives): a region of public geometry, a centre
 * cell and a radius in cells, square. Its places are the garrisonables whose centre lies inside it.
 */
public record PlaceTarget(int x, int y, int radius) {

    public PlaceTarget {
        if (radius < 0) throw new IllegalArgumentException("Negative place radius " + radius);
    }

    /** The cells within {@code radius} of {@code (x, y)} on both axes. */
    public static PlaceTarget region(int x, int y, int radius) {
        return new PlaceTarget(x, y, radius);
    }

    /** One cell. */
    public static PlaceTarget cell(int x, int y) {
        return new PlaceTarget(x, y, 0);
    }

    /** Whether the cell lies in this region. */
    public boolean contains(int cellX, int cellY) {
        return Math.abs(cellX - x) <= radius && Math.abs(cellY - y) <= radius;
    }

    /** Whether the point (cells, fractional) lies in this region, by the cell it stands in. */
    public boolean contains(float cellX, float cellY) {
        return contains((int) Math.floor(cellX), (int) Math.floor(cellY));
    }

    @Override public String toString() {
        return "(" + x + "," + y + ")r" + radius;
    }
}
