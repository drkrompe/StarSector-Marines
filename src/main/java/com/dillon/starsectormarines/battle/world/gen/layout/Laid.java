package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.List;

/**
 * What generation made of one piece: its cells as laid, the ways left through
 * it ({@code gaps}: one cell in each road gap, and the open cells of each wire
 * funnel), and its nests. A site's {@code cells} are its two corners.
 */
public record Laid(String name, Kind kind, Status status, String reason, boolean required,
                   List<int[]> cells, List<int[]> gaps, List<int[]> nests) {

    /**
     * A line of works, an open site, ground held for authored terrain ({@link SitePurpose}), or a road carved into
     * the network (its {@code gaps} are its two ends), a structure (its two corners, as a site's) or a block. A
     * {@link #YARD} is no layout piece: a walled place's gate yard as its ward laid it
     * ({@code GateYardLayout}), its {@code cells} the yard, its {@code gaps} the gate, entry, turn and stop
     * cells in that order, and its {@code nests} the staging ground beside it.
     */
    public enum Kind { LINE, SITE, GROUND, ROAD, STRUCTURE, BLOCK, YARD }

    public enum Status {
        /** Laid as asked. */
        LAID,
        /** Laid where the ground allowed: buildings, roads or a seal broke it. */
        PARTIAL,
        /** Not laid; {@link Laid#reason} says why. */
        REFUSED
    }

    public Laid {
        reason = reason == null ? "" : reason;
        cells = cells == null ? List.of() : List.copyOf(cells);
        gaps = gaps == null ? List.of() : List.copyOf(gaps);
        nests = nests == null ? List.of() : List.copyOf(nests);
    }

    /** A piece that was not laid at all. */
    public static Laid refused(String name, Kind kind, boolean required, String reason) {
        return new Laid(name, kind, Status.REFUSED, reason, required, List.of(), List.of(), List.of());
    }

    public boolean isLaid() { return status != Status.REFUSED; }
}
