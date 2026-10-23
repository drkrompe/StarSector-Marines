package com.dillon.starsectormarines.battle.world.gen.layout;

/** What a {@link Line} is built from. */
public enum LineStyle {
    /** A trench with a sandbag parapet and a wire (fence) course in front of it, opened in funnels. */
    TRENCH_WIRE,
    /** A trench with a sandbag parapet: walkable, closes nothing. */
    TRENCH,
    /** A sandbag course facing the front: walkable cover, closes nothing. */
    SANDBAG,
    /**
     * A line through built ground: a sandbag course where the course is open, and where a house stands across it,
     * sandbags along the house's front instead. Walkable cover, closes nothing (its barricaded gaps aside).
     */
    STREETS;

    /** Whether the line is dug: a trench, its parapet and the worked ground either side. */
    public boolean dug() {
        return this == TRENCH_WIRE || this == TRENCH;
    }
}
