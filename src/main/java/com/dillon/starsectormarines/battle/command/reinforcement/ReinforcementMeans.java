package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;

/**
 * A way to deliver reinforcements to a battle. The {@link ReinforcementSystem}
 * iterates registered means in priority order on each request; the first one to
 * return {@code canFulfill = true} wins and {@link #dispatch}es.
 *
 * <p>The production defender ladder is {@link ConvoyMeans},
 * {@link ShuttleMeans}, then {@link WalkInMeans}. Each is supply- and
 * map-feasibility-gated; see {@code reinforcement-nouns.md}.
 */
public interface ReinforcementMeans {

    /**
     * Can this means deliver the given request on the current map?
     * Convoy needs a road graph and a reachable rally; shuttle needs an
     * LZ; walk-in needs a usable perimeter cell. Cheap probe — called
     * once per request per means provider.
     */
    boolean canFulfill(BattleView sim, ReinforcementRequest req);

    /**
     * Spawn ordinary vehicle, air, or squad actors through their native battle
     * lifecycle. Called only after {@link #canFulfill} returns {@code true}.
     */
    void dispatch(BattleControl sim, ReinforcementRequest req);
}
