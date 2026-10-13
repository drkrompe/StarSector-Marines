package com.dillon.starsectormarines.battle.fort;

/**
 * Which fort a defence-post structure belongs to and which part of it the structure is. Carried from the map's
 * {@code DefensePost} to the simulation so the fort is assembled from what was actually stamped, not rediscovered.
 *
 * @param fortId a map-unique, non-negative fort number
 * @param aspect the aspect this structure is the core of, or {@code null} for the battery platform and for the
 *               fort's wall nests
 */
public record FortTag(int fortId, FortAspect aspect) {
    public FortTag {
        if (fortId < 0) throw new IllegalArgumentException("fortId may not be negative");
    }
}
