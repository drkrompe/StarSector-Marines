package com.dillon.starsectormarines.battle.warship;

/**
 * One of the four edges of a battle map, as a side's way on and off it. A mission may give each side an approved edge
 * ({@link WarshipSystem#assignEdge}): its warships then come on by flying in across it and leave by flying out across
 * it, and nothing warps in or out.
 *
 * <p>Cells run {@code x} east and {@code y} north, so {@link #SOUTH} is the row {@code y = 0}. Headings are the hull
 * convention ({@link WarshipFacing}): the nose lies along {@code (-sin h, cos h)}, so due east is 270.
 */
public enum MapEdge {
    WEST(-1f, 0f, 270f),
    EAST(1f, 0f, 90f),
    SOUTH(0f, -1f, 0f),
    NORTH(0f, 1f, 180f);

    /** The outward normal: the direction that crosses this edge off the map. */
    public final float outX, outY;
    /** The hull heading straight in across this edge. */
    public final float inwardHeading;

    MapEdge(float outX, float outY, float inwardHeading) {
        this.outX = outX;
        this.outY = outY;
        this.inwardHeading = inwardHeading;
    }

    /** How far inside this edge {@code (x, y)} lies on a {@code width} by {@code height} map; negative beyond it. */
    public float inside(float x, float y, float width, float height) {
        return switch (this) {
            case WEST -> x;
            case EAST -> width - x;
            case SOUTH -> y;
            case NORTH -> height - y;
        };
    }

    /** Whether {@code (x, y)} lies beyond this edge, off the map. */
    public boolean beyond(float x, float y, float width, float height) {
        return inside(x, y, width, height) < 0f;
    }

    /**
     * The point {@code along} cells along this edge (its {@code y} for west and east, its {@code x} for south and
     * north) and {@code out} cells outside it ({@code out} negative is inside).
     *
     * @return {@code [x, y]}
     */
    public float[] point(float along, float out, float width, float height) {
        return switch (this) {
            case WEST -> new float[]{-out, along};
            case EAST -> new float[]{width + out, along};
            case SOUTH -> new float[]{along, -out};
            case NORTH -> new float[]{along, height + out};
        };
    }

    /** The coordinate of {@code (x, y)} along this edge: its {@code y} for west and east, its {@code x} otherwise. */
    public float along(float x, float y) {
        return this == WEST || this == EAST ? y : x;
    }
}
