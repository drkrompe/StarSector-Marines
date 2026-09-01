package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

/**
 * Where a fortress ward stands and which way it faces.
 *
 * <p>Everything else the ward needs is handed to it — the program it owes, the
 * road it keeps, the compound it must not build over. Placement was the one
 * exception, worked out from the biome band, and it is the exception that tied
 * a fortress to a traversal axis: no axis, no band, no fortress. Splitting it
 * out makes the band one way of answering the question rather than the only
 * one, which is what lets a map be built outward from its installation instead
 * of sliced into percentile bands along an approach.
 *
 * <p>{@code facing} is not decoration either. The ward packs into depth bands
 * measured from the attacker's approach, its airbase takes its lot from the
 * ward's own end, and both need to know which end that is. A fortress with city
 * on every side still faces somewhere — the side its main gate is on — so this
 * stays meaningful when the axis stops being the map's organising idea.
 *
 * @param rect   inclusive {@code {x0, y0, x1, y1}} the ward may lay itself into
 * @param facing the approach the ward is arranged against
 */
public record WardSite(int[] rect, TraversalAxis facing) {

    public WardSite {
        if (rect == null || rect.length != 4) {
            throw new IllegalArgumentException("a ward site is an inclusive x0,y0,x1,y1 rect");
        }
        if (facing == null) throw new IllegalArgumentException("a ward faces somewhere");
    }

    /**
     * Chooses the ground a ward is laid into.
     *
     * <p>Returns {@code null} when this map has no fortress to place — an
     * absent biome band, a district too shallow to pack — which is a map
     * without one rather than an error.
     */
    public interface Planner {
        WardSite plan(GenContext ctx);
    }
}
