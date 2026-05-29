package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;

/**
 * A way to deliver reinforcements to a battle. The {@link ReinforcementService}
 * iterates registered means in priority order on each request; first one to
 * return {@code canFulfill = true} wins and {@link #dispatch}es.
 *
 * <p>Planned v1 set: {@code ConvoyMeans} (road graph + truck),
 * {@code ShuttleMeans} (existing air infra, ported), {@code WalkInMeans}
 * (perimeter spawn + walk to rally). Convoy is the only one wired today;
 * walk-in becomes the always-feasible floor once it lands.
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
     * Spawn the actual units. May post {@link com.dillon.starsectormarines.battle.Vehicle},
     * {@code Shuttle}, or {@code Squad}/{@code Unit} into the sim's normal
     * lists. Called only after {@link #canFulfill} returns {@code true}.
     */
    void dispatch(BattleControl sim, ReinforcementRequest req);
}
