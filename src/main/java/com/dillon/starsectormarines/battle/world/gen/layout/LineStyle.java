package com.dillon.starsectormarines.battle.world.gen.layout;

/** What a {@link Line} is built from. */
public enum LineStyle {
    /** A trench with a wire (fence) course in front of it, opened in funnels. */
    TRENCH_WIRE,
    /** A trench alone: walkable, closes nothing. */
    TRENCH,
    /** A sandbag course facing the front: walkable cover, closes nothing. */
    SANDBAG
}
