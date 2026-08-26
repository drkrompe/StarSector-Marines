package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * The authored longitudinal shape of one ship deck — the ship family's answer
 * to a station's rings and core.
 *
 * <p>A deck runs bow to stern along +x. Each x is a <b>frame</b>, and every
 * frame declares the inclusive interior rows the hull encloses there. Beam
 * therefore varies along the length: narrow at the bow, broadest amidships,
 * blunter at the stern, and free to differ port from starboard. A deck is never
 * a rectangle and never mirrored fore to aft.
 *
 * <p>The <b>spine</b> is the fore-aft circulation corridor. It occupies the
 * same rows at every frame and is enclosed by bulkhead on both sides, so
 * compartments open onto it through carved doors rather than bleeding into it.
 *
 * <p>Immutable and published on the context by {@code HullProfileStage}.
 * Consumers ask this for frame, zone, and interior bounds; they never re-derive
 * them from cell coordinates. See {@code ship-interiors-nouns.md}.
 */
public final class DeckProfile {

    private final int spineTop;
    private final int spineBottom;
    private final int[] top;
    private final int[] bottom;
    private final DeckZone[] zone;

    /**
     * @param spineTop first walkable spine row (inclusive)
     * @param spineBottom last walkable spine row (inclusive)
     * @param top per-frame topmost interior row, inclusive; never below {@code spineTop}
     * @param bottom per-frame bottommost interior row, inclusive; never above {@code spineBottom}
     * @param zone per-frame longitudinal zone
     */
    public DeckProfile(int spineTop, int spineBottom, int[] top, int[] bottom, DeckZone[] zone) {
        if (top.length != bottom.length || top.length != zone.length) {
            throw new IllegalArgumentException("deck profile arrays must agree on frame count");
        }
        if (top.length == 0) throw new IllegalArgumentException("a deck needs at least one frame");
        if (spineBottom < spineTop) throw new IllegalArgumentException("spine rows are inverted");
        for (int f = 0; f < top.length; f++) {
            if (top[f] > spineTop || bottom[f] < spineBottom) {
                throw new IllegalArgumentException(
                        "frame " + f + " does not enclose the spine; the corridor runs the full length");
            }
        }
        this.spineTop = spineTop;
        this.spineBottom = spineBottom;
        this.top = top.clone();
        this.bottom = bottom.clone();
        this.zone = zone.clone();
    }

    /** Number of frames bow to stern; equal to the map width. */
    public int frames() {
        return top.length;
    }

    /** First walkable spine row (inclusive). */
    public int spineTop() {
        return spineTop;
    }

    /** Last walkable spine row (inclusive). */
    public int spineBottom() {
        return spineBottom;
    }

    /** Walkable width of the spine corridor, in cells. */
    public int spineWidth() {
        return spineBottom - spineTop + 1;
    }

    /** Topmost interior row at {@code frame}, inclusive. */
    public int top(int frame) {
        return top[frame];
    }

    /** Bottommost interior row at {@code frame}, inclusive. */
    public int bottom(int frame) {
        return bottom[frame];
    }

    /** Interior height at {@code frame}, in cells — the deck's beam there. */
    public int beam(int frame) {
        return bottom[frame] - top[frame] + 1;
    }

    /** The longitudinal zone {@code frame} belongs to. */
    public DeckZone zone(int frame) {
        return zone[frame];
    }

    /** Whether the hull encloses this cell; cells outside remain solid hull for the whole run. */
    public boolean containsCell(int x, int y) {
        if (x < 0 || x >= top.length) return false;
        return y >= top[x] && y <= bottom[x];
    }
}
