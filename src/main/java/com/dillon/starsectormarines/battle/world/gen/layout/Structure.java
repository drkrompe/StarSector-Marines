package com.dillon.starsectormarines.battle.world.gen.layout;

/**
 * A structure the mission raises at a place it chose ({@code mission-layout.md}):
 * its footprint's lower-left corner {@code (x, y)} in world cells, turned
 * {@code turns} quarter turns (an odd count swaps its sides). Raised in the
 * structures phase, before the map is finished, so its buildings are
 * buildings like any other.
 */
public record Structure(String name, StructureKind kind, int x, int y, int turns, boolean required) implements Piece {

    public Structure {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("a structure needs a name");
        if (kind == null) throw new IllegalArgumentException("a structure has a kind: " + name);
        turns = Math.floorMod(turns, 4);
    }

    /** Cells the footprint spans along x and y, as turned. */
    public int width() { return (turns & 1) == 0 ? kind.along : kind.deep; }

    public int height() { return (turns & 1) == 0 ? kind.deep : kind.along; }
}
