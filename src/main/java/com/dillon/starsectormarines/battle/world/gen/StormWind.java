package com.dillon.starsectormarines.battle.world.gen;

/**
 * Which side a storm world's wind comes from ({@code mapgen-nouns.md}, Climate): one side of the map, drawn once per
 * planet ({@link Climate#wind}), so every weather adaptation on a map faces the same way. Y is up, as
 * {@code MapPlacement}'s is: {@link Side#NORTH} is the high-y edge.
 */
public final class StormWind {

    /** The side the wind comes from. */
    public enum Side {
        NORTH(0, 1), SOUTH(0, -1), WEST(-1, 0), EAST(1, 0);

        /** One step toward this side. */
        public final int dx, dy;

        Side(int dx, int dy) { this.dx = dx; this.dy = dy; }

        /** Whether this side runs along the x axis (the wind crosses y). */
        public boolean alongX() { return dx == 0; }

        /** Whether this side is the low end of its axis (west, south). */
        public boolean low() { return dx + dy < 0; }
    }

    private StormWind() { }

    /** The windward side a seed draws. */
    public static Side windward(long seed) {
        return Side.values()[(int) Math.floorMod(seed ^ 0x53544f524dL, 4L)];
    }
}
