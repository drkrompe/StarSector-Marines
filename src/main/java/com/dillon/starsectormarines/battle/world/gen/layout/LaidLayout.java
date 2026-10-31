package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.List;
import java.util.Map;

/**
 * Every piece of a map's {@link MissionLayout}, as generation laid it; empty on a map with no layout.
 *
 * <p>{@code onWays} names each laid structure or line of works that stands on a way (a road, a farm track or a
 * landscape track, {@link LayoutGround#isWay}), with the cells it covers there, read once the layout was laid: the
 * acceptance reading of "nothing is built on a road" ({@code mission-layout.md}). A square, paving itself, is not
 * counted; blocks are meant to close roads and are not either.
 */
public record LaidLayout(List<Laid> pieces, Map<String, List<int[]>> onWays) {

    public static final LaidLayout EMPTY = new LaidLayout(List.of());

    public LaidLayout {
        pieces = pieces == null ? List.of() : List.copyOf(pieces);
        onWays = onWays == null ? Map.of() : Map.copyOf(onWays);
    }

    public LaidLayout(List<Laid> pieces) {
        this(pieces, Map.of());
    }

    /** The piece of this name, or {@code null} when the layout produced none. */
    public Laid find(String name) {
        for (Laid laid : pieces) if (laid.name().equals(name)) return laid;
        return null;
    }

    /** Whether a piece of this name stands on the map, whole or in part. */
    public boolean has(String name) {
        Laid laid = find(name);
        return laid != null && laid.isLaid();
    }
}
