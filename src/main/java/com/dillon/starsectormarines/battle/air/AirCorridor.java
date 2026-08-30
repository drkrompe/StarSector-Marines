package com.dillon.starsectormarines.battle.air;

/**
 * The lane an off-map sortie enters and leaves the battle by: a named source,
 * a point outside the map it flies in from, and a point outside the map it
 * leaves across.
 *
 * <p><b>An origin is a property of a sortie, not a kind of aircraft.</b> The
 * same {@link Airframe} flies the same attack runs whether it rolled off a
 * berth on the map or crossed the boundary from a carrier overhead; what
 * differs is where it came from and what it owes itself back to. A berth is one
 * origin and a corridor is the other, and everything between arriving on
 * station and turning for home is identical.
 *
 * <p><b>A corridor is stated, never discovered.</b> Both points are off the
 * map by construction, so an off-map sortie cannot be conjured onto ground it
 * does not own: there is no cell it could appear at and none it could vanish
 * into. That rule is the whole reason this type exists rather than a pair of
 * floats picked at the dispatch site — a dispatch that fell back to "the
 * nearest suitable place on the map" once put eighteen of twenty-four sorties
 * on hardstands nobody had assigned them, several of them on the same one, and
 * deleted each craft on arrival. A dispatcher that cannot state a corridor
 * declines the sortie; see {@code air-nouns.md}.
 *
 * <p>The edge is chosen from where the sortie's own side is, because that is
 * the direction its carrier is overhead in and the direction a player reads as
 * "ours". Along that edge the entry sits abeam the target, so the run-in is
 * roughly straight at what the aircraft was sent for, and the exit sits abeam
 * home, so it leaves back over its own force rather than across the enemy's.
 */
public final class AirCorridor {

    /**
     * How far outside the map the entry and exit points sit.
     *
     * <p>Far enough that a craft is well clear of the boundary before anything
     * on the map could reach it, and near enough that the leg in is a flight
     * rather than a wait. The altitude lerp runs over this distance, so a
     * shorter corridor would have the aircraft arriving on station still
     * descending.
     */
    public static final float OFF_MAP_MARGIN_CELLS = 10f;

    /** Who this sortie is from, in the words a player would recognise. */
    public final String source;

    /** Where the craft crosses onto the map, in cells. Always outside it. */
    public final float entryX, entryY;

    /** Where the craft crosses back off, in cells. Always outside it. */
    public final float exitX, exitY;

    private AirCorridor(String source, float entryX, float entryY, float exitX, float exitY) {
        this.source = source;
        this.entryX = entryX;
        this.entryY = entryY;
        this.exitX = exitX;
        this.exitY = exitY;
    }

    /**
     * A corridor across the map edge nearest {@code (homeX, homeY)}, entering
     * abeam {@code (targetX, targetY)} and leaving abeam home.
     *
     * @return the corridor, or {@code null} when the map has no extent — which
     *         is a decline, not a fallback: a caller that cannot be told where
     *         off the map is must not invent somewhere on it.
     */
    public static AirCorridor acrossNearestEdge(String source, int mapCellsW, int mapCellsH,
                                                float homeX, float homeY,
                                                float targetX, float targetY) {
        if (mapCellsW <= 0 || mapCellsH <= 0) return null;
        float w = mapCellsW;
        float h = mapCellsH;
        // Which boundary is home closest to. Ties resolve west-before-east and
        // south-before-north by the comparison order, which is arbitrary and
        // stable — the point is that one battle always answers the same way.
        float toWest = homeX;
        float toEast = w - homeX;
        float toSouth = homeY;
        float toNorth = h - homeY;
        float nearest = Math.min(Math.min(toWest, toEast), Math.min(toSouth, toNorth));
        if (nearest == toWest || nearest == toEast) {
            float x = (nearest == toWest) ? -OFF_MAP_MARGIN_CELLS : w + OFF_MAP_MARGIN_CELLS;
            return new AirCorridor(source, x, clamp(targetY, h), x, clamp(homeY, h));
        }
        float y = (nearest == toSouth) ? -OFF_MAP_MARGIN_CELLS : h + OFF_MAP_MARGIN_CELLS;
        return new AirCorridor(source, clamp(targetX, w), y, clamp(homeX, w), y);
    }

    /** Keeps the along-edge coordinate on the map's own span, so a corridor never runs off a corner. */
    private static float clamp(float v, float span) {
        if (v < 0f) return 0f;
        if (v > span) return span;
        return v;
    }

    @Override
    public String toString() {
        return "AirCorridor[" + source + " in (" + entryX + "," + entryY
                + ") out (" + exitX + "," + exitY + ")]";
    }
}
