package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.List;

/** Every piece of a map's {@link MissionLayout}, as generation laid it; empty on a map with no layout. */
public record LaidLayout(List<Laid> pieces) {

    public static final LaidLayout EMPTY = new LaidLayout(List.of());

    public LaidLayout {
        pieces = pieces == null ? List.of() : List.copyOf(pieces);
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
