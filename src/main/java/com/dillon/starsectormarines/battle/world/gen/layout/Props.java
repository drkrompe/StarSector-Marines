package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.List;

/**
 * Props a mission sets down by hand ({@code mission-layout.md}): each a doodad by id with its low corner at a cell,
 * turned by quarter turns, laid with the works through the ordinary prop placement, which quietly skips one whose
 * ground is not open. Reported as one piece: laid when any prop stood, with how many did.
 */
public record Props(String name, List<Placement> props, boolean required) implements Piece {

    /** One prop: the doodad's id, its low corner and its quarter turns. */
    public record Placement(String doodad, int x, int y, int quarterTurns) { }

    public Props {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("props need a name");
        if (props == null || props.isEmpty()) throw new IllegalArgumentException("props need placements: " + name);
        props = List.copyOf(props);
    }
}
