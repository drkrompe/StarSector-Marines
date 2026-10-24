package com.dillon.starsectormarines.battle.world.model;

/**
 * What has happened to the roof over one cell (`battle-render-nouns.md`, Roofs): how it is drawn once it is no longer
 * whole. Every state but {@link #INTACT} is a roof open to the sky, which is all a rule reads
 * ({@link CellTopology#isRoofDestroyed}); the states differ only in what the damage was, so a roof shows what hit it.
 *
 * <p>The states are ordered by how little is left, and a second blow keeps the worse ({@link #worse}): a holed roof
 * that a wall then brings down is collapsed, and a collapsed one that then burns is burnt through. Nothing heals a
 * roof but a rebuild ({@link CellTopology#setRoofDestroyed} false), which clears it.
 */
public enum RoofDamage {
    /** Whole. */
    INTACT,
    /** A shell or a light bomb has punched through: a ragged hole with the roof standing round it. */
    HOLED,
    /** The roof has fallen in: a heavy bomb, a crash, or the wall under it coming down. A sagging remnant is left. */
    COLLAPSED,
    /** Fire has burnt the deck away: charred rafters and scorched remnants over the room. */
    BURNT;

    private static final RoofDamage[] VALUES = values();

    /** The state with less roof left of the two. */
    public RoofDamage worse(RoofDamage other) {
        return other.ordinal() > ordinal() ? other : this;
    }

    /** Whether anything is open to the sky. */
    public boolean isOpen() {
        return this != INTACT;
    }

    /** The state with this {@link #ordinal}; {@link #INTACT} for anything out of range. */
    public static RoofDamage ofOrdinal(int ordinal) {
        return ordinal > 0 && ordinal < VALUES.length ? VALUES[ordinal] : INTACT;
    }

    /**
     * The fraction of a blast's radius inside which a heavy blast brings the roof down rather than holing it.
     *
     * <p>The middle of a big blast collapses what is over it and its edge holes it; a small one only holes.
     */
    static final float COLLAPSE_CORE = 0.55f;

    /**
     * A blast at least this wide (cells of splash radius) is heavy enough to bring a roof down: an aircraft's bombs
     * (3.6 and 4 cells) are, a mortar round or a missile (2.2 to 2.8) is not.
     */
    public static final float HEAVY_BLAST_RADIUS = 3f;

    /**
     * What a blast does to the roof of a cell {@code distance} cells from its centre: a heavy blast
     * ({@link #HEAVY_BLAST_RADIUS} and up) collapses its core and holes the rest; a lighter one holes.
     */
    public static RoofDamage ofBlast(float radius, float distance) {
        return radius >= HEAVY_BLAST_RADIUS && distance <= radius * COLLAPSE_CORE ? COLLAPSED : HOLED;
    }
}
