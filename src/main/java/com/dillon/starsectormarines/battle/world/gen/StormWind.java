package com.dillon.starsectormarines.battle.world.gen;

/**
 * Which side a storm world's wind comes from ({@code mapgen-nouns.md}, Climate): one side of the map, drawn from the
 * seed, so every weather adaptation on a map faces the same way.
 */
public final class StormWind {

    /** The side the wind comes from. */
    public enum Side { NORTH, SOUTH, WEST, EAST }

    private StormWind() { }

    /** The windward side a seed draws. */
    public static Side windward(long seed) {
        return Side.values()[(int) Math.floorMod(seed ^ 0x53544f524dL, 4L)];
    }
}
