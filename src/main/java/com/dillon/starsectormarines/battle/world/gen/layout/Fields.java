package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Plot;

import java.util.List;

/**
 * Farmed ground a mission lays ({@code mission-layout.md}): crop plots painted as the colony's fields are, grain spilt
 * where a harvest is handled, and pond water. Laid in the works phase; a cell that is not open ground keeps what it is.
 * Fields and spill change only how the ground looks. Water closes its cells, so whoever plans it keeps a dry ring
 * round it. Reported as one piece: laid when any cell was.
 *
 * @param plots crop plots, painted by their use
 * @param spill cells of spilt grain
 * @param water cells of pond water
 */
public record Fields(String name, List<Plot> plots, List<int[]> spill, List<int[]> water, boolean required)
        implements Piece {
    public Fields {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("fields need a name");
        plots = List.copyOf(plots);
        spill = List.copyOf(spill);
        water = List.copyOf(water);
        if (plots.isEmpty() && spill.isEmpty() && water.isEmpty()) {
            throw new IllegalArgumentException("fields need ground: " + name);
        }
    }
}
