package com.dillon.starsectormarines.battle.world.gen.fortress;

import java.util.List;

/**
 * One way an attacker can go at a fort, in the fort's own local frame: the part of a stamp's assault plan that lives with
 * the stamp (`phased-assault.md`). A plan names where the way in is, which of the fort's nests have it under fire, and
 * where the entry cell lies inside the wall; what a company does with it (who fires, who goes in, when) is the
 * command's, and reads only what the commander has been disclosed of the works.
 *
 * <p>Three variants. {@link Variant#MAIN_GATE} is the first gate of the plan, {@link Variant#POSTERN} is any other, and
 * {@link Variant#BREACH} is a section of wall far from every nest, for a company that carries demolition and would
 * rather cut a way in than walk up a gate the nests cover.
 *
 * @param variant   which kind of way in
 * @param gateIndex index into {@link FortPlan#gates()}, or {@code -1} for a breach
 * @param mouthX    the open cell just outside the way in
 * @param mouthY    as {@code mouthX}
 * @param outX      the cardinal step outward from the way in
 * @param outY      as {@code outX}
 * @param entryX    a cell inside the wall, past the gatehouse (or through the breach), where the way in is made good
 * @param entryY    as {@code entryX}
 * @param covering  indices into {@link FortPlan#defenceNests()} of the nests that have the mouth within their reach
 * @param lane      the approach lane in front of the way in, from the mouth outward to the footprint's edge: the cells
 *                  the belt and fence are left open across
 */
public record AssaultApproach(Variant variant, int gateIndex, int mouthX, int mouthY, int outX, int outY,
                              int entryX, int entryY, List<Integer> covering, List<int[]> lane) {

    /** The ways in. */
    public enum Variant { MAIN_GATE, POSTERN, BREACH }

    public AssaultApproach {
        covering = List.copyOf(covering);
        lane = List.copyOf(lane);
    }
}
