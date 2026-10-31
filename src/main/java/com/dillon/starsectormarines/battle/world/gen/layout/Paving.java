package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.List;

/**
 * Street paving the mission lays over cells it chose ({@code mission-layout.md}): each open cell becomes street ground
 * before the map is finished, so the city's road finish dresses it as any street, carriageway, kerb and centre line.
 * Laid in the structures phase; a cell that is not open ground (a wall, a building, a prop) keeps what it is.
 */
public record Paving(String name, List<int[]> cells, boolean required) implements Piece {

    public Paving {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("paving needs a name");
        cells = List.copyOf(cells);
    }
}
