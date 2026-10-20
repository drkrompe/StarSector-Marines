package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.List;

/**
 * Which compound a garrisonable fronts: the prepared ground a compound's door or face is given
 * ({@code CompoundPreparedGround}) belongs to the compound whose building it stands off, the way a belt belongs to its
 * fort, and a building's envelope to the compound whose building it is. Read at a stretch's centre against each compound's own building box grown by {@link #REACH_CELLS}; a place
 * attributed to a fort is a belt and fronts no compound. Pure lookup over the commander's own disclosed compounds.
 */
final class CompoundGround {

    /** How far outside a compound building's box its apron may stand: the longest standoff, its return, and a cell. */
    static final int REACH_CELLS = 7;

    private CompoundGround() {}

    /**
     * The compound this place fronts, nearest first, or {@code null}. A building's envelope belongs to the compound whose
     * building it is.
     */
    static ConquestCommandFacts.Compound frontedBy(GarrisonableRegistry places, int place,
                                                   List<ConquestCommandFacts.Compound> compounds) {
        if (places == null || place < 0 || place >= places.count()) return null;
        if (places.isEnvelope(place)) {
            TacticalNode building = places.building(place);
            for (ConquestCommandFacts.Compound compound : compounds) {
                if (GarrisonableRegistry.sameBuilding(compound.node(), building)) return compound;
            }
            return null;
        }
        if (places.fortId(place) >= 0) return null;
        int x = places.centreX(place);
        int y = places.centreY(place);
        ConquestCommandFacts.Compound best = null;
        int bestGap = Integer.MAX_VALUE;
        for (ConquestCommandFacts.Compound compound : compounds) {
            TacticalNode node = compound.node();
            int gap = Math.max(Math.max(node.left - x, x - node.right), Math.max(node.top - y, y - node.bottom));
            if (gap > REACH_CELLS || gap >= bestGap) continue;
            best = compound;
            bestGap = gap;
        }
        return best;
    }
}
