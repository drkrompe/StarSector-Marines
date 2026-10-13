package com.dillon.starsectormarines.battle.fort;

/**
 * Which fort a defence-post structure belongs to and which part of it the structure is. Carried from the map's
 * {@code DefensePost} to the simulation so the fort is assembled from what was actually stamped, not rediscovered.
 *
 * <p>It also carries the fort's placement (the footprint origin and its cardinal turn), so the simulation and the
 * renderer can map the fort's authored local frame back onto the map without the generator's context: the building
 * art is drawn in that frame.
 *
 * @param fortId       a map-unique, non-negative fort number
 * @param aspect       the aspect this structure is the core of, or {@code null} for the battery platform and for the
 *                     fort's wall nests
 * @param originX      world column of the footprint's local origin, as {@code Placement#x}
 * @param originY      world row of the footprint's local origin, as {@code Placement#y}
 * @param quarterTurns the cardinal turn the fort was placed with, 0 to 3, or {@link #UNPLACED} for a tag made without
 *                     a placement (a test), in which case no frame exists and nothing is drawn from it
 */
public record FortTag(int fortId, FortAspect aspect, int originX, int originY, int quarterTurns) {

    /** The turn of a tag that carries no placement. */
    public static final int UNPLACED = -1;

    public FortTag {
        if (fortId < 0) throw new IllegalArgumentException("fortId may not be negative");
        if (quarterTurns < UNPLACED || quarterTurns > 3) {
            throw new IllegalArgumentException("quarterTurns must be 0..3 or UNPLACED");
        }
    }

    /** A tag with no placement, for callers that only need the fort and the aspect. */
    public FortTag(int fortId, FortAspect aspect) {
        this(fortId, aspect, 0, 0, UNPLACED);
    }

    /** Whether this tag knows where its fort was stamped. */
    public boolean placed() {
        return quarterTurns != UNPLACED;
    }
}
