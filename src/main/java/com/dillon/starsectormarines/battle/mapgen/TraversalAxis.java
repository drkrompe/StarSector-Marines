package com.dillon.starsectormarines.battle.mapgen;

/**
 * The progression direction of a conquest map. The attacker enters from the
 * {@code start} side and pushes toward the {@code end} side; biome bands lay
 * out along this axis (beach at start, fortress at end), and the super-wall
 * pass puts gates on the attacker-facing edge of the fortress.
 *
 * <p>Per-map. Each generated map picks one axis at gen time. The generator
 * passes the axis to {@link com.dillon.starsectormarines.battle.mapgen.bsp.BiomeMap}
 * so the same biome list lays out vertically or horizontally as needed.
 */
public enum TraversalAxis {
    /** Attacker enters from the south (y=0), pushes north. Biome bands run east-west. */
    SOUTH_TO_NORTH,
    /** Attacker enters from the west (x=0), pushes east. Biome bands run north-south. */
    WEST_TO_EAST,
}
