package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

/**
 * Whether an ordinary ground click is really an order to <b>hold a building</b>: the click
 * landed inside a held place's footprint, so the squad is asked to garrison it — stand to at
 * its windows when the enemy is believed close, walk its rooms when not — rather than to
 * walk to it. The building counterpart of {@link OccupyPositionDecision}, asked by the order
 * system and the cursor preview alike so the verb the cursor promises is the one the click
 * gives.
 *
 * <p>Pure: no world, no state. Callers decide which squads the question applies to (infantry
 * only) and resolve the more specific answers — a ride, an uncaptured compound, a trench —
 * first.
 */
public final class HoldPlaceDecision {

    private HoldPlaceDecision() { }

    /**
     * The held place a click at ({@code cellX}, {@code cellY}) lands in, or null. Where two
     * footprints overlap the smaller is the one pointed at, since it is the more specific;
     * ties fall to map order so the answer is deterministic.
     *
     * @param map the battle's tactical map; may be {@code null}
     */
    public static TacticalNode placeUnderClick(TacticalMap map, int cellX, int cellY) {
        if (map == null) return null;
        TacticalNode best = null;
        long bestArea = Long.MAX_VALUE;
        for (TacticalNode node : map.all()) {
            if (node.kind != TacticalNode.Kind.INNER_POSITION) continue;
            if (cellX < node.left || cellX > node.right
                    || cellY < node.top || cellY > node.bottom) continue;
            long area = (long) (node.right - node.left + 1) * (node.bottom - node.top + 1);
            if (area < bestArea) {
                best = node;
                bestArea = area;
            }
        }
        return best;
    }
}
