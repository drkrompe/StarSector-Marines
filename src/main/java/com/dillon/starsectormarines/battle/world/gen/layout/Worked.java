package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.List;

/**
 * Ground a mission works by hand ({@code mission-layout.md}): bare trodden earth over the cells, a hamlet's lane or a
 * yard, nothing built. Laid in the phase of the deferred piece that resolved it, so a hamlet's lane goes down with its
 * buildings; a cell whose ground is not open is skipped. Reported as one piece: laid when any cell was worked.
 */
public record Worked(String name, List<int[]> cells, boolean required) implements Piece {

    public Worked {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("worked ground needs a name");
        if (cells == null || cells.isEmpty()) throw new IllegalArgumentException("worked ground needs cells: " + name);
        cells = List.copyOf(cells);
    }
}
