package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.List;

/**
 * Cells the mission closes or fortifies by hand ({@code mission-layout.md}): a
 * barricade, a wreck across a road, or sandbag positions facing
 * {@code facing} (a world direction; only sandbags need one). Laid with the
 * works; a block that would seal the ground is not laid.
 */
public record Block(String name, List<int[]> cells, BlockKind kind, int[] facing, boolean required) implements Piece {

    public Block {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("a block needs a name");
        if (cells == null || cells.isEmpty()) throw new IllegalArgumentException("a block needs cells: " + name);
        if (kind == null) throw new IllegalArgumentException("a block has a kind: " + name);
        if (kind == BlockKind.SANDBAG && (facing == null || facing.length != 2)) {
            throw new IllegalArgumentException("sandbags face somewhere: " + name);
        }
        cells = List.copyOf(cells);
    }
}
