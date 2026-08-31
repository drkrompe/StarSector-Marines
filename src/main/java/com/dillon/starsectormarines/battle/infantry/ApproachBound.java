package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.Paths;

/**
 * Whether a walk to a chosen cell is one worth taking.
 *
 * <p>Every firing-position picker bounds the <em>straight-line</em> distance
 * from an anchor — a hold ring, a patrol leash, an area radius, a route
 * anchor — while the member that has to stand there must <em>walk</em>. A wall
 * makes those two numbers diverge without limit: measured on a probe, a cell
 * three from its anchor and seven from the marine cost a thirty-nine cell
 * march around the building between them. A leash that bounds the first
 * number and not the second is not bounding anything a marine experiences.
 *
 * <p>Kept apart from the movement idioms that consult it because there are
 * two of those and only one question. An advancing order re-picks its
 * destination every tick and repaths to it; a garrison walks to a cell and
 * keeps walking until it arrives. Both need the same answer about the same
 * path, and neither should own the definition.
 */
public final class ApproachBound {

    /**
     * How much further than the straight line a member may walk to reach a
     * firing position, as a multiple of that straight line.
     */
    public static final float DETOUR_RATIO = 2.5f;
    /**
     * Cells of travel allowed before the ratio applies, so a position one or
     * two cells away is not refused for stepping around a crate.
     */
    public static final float DETOUR_SLACK = 4f;

    private ApproachBound() {}

    /**
     * Whether {@code path} is a walk worth taking to reach {@code (toX, toY)}:
     * it exists at all, and it is not a detour out of proportion to the
     * straight line it stands in for.
     *
     * <p>An empty path is always refused. The detour test is the caller's
     * choice, because it is only meaningful for a destination picked as an
     * improvement — going home or investigating a noise the long way round is
     * the right thing to do when the long way is the only way.
     */
    public static boolean worthWalkingTo(int fromX, int fromY, int toX, int toY,
                                         int[] path, boolean boundDetour) {
        if (Paths.isEmpty(path)) return false;
        if (!boundDetour) return true;
        float straight = TacticalScoring.cellDistance(fromX, fromY, toX, toY);
        return Paths.cellCount(path) <= DETOUR_SLACK + DETOUR_RATIO * straight;
    }
}
