package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.function.Consumer;

/**
 * A source of {@link ReinforcementRequest}s. The {@link ReinforcementService}
 * polls every registered trigger each slow-tick; the trigger walks the sim
 * and hands any new requests to {@code out}.
 *
 * <p>Triggers own their own state — e.g. {@link GarrisonDepletedTrigger}
 * keeps a per-compound posted-flag so a depleted garrison doesn't refire
 * once it's been answered. The service is stateless across triggers.
 */
public interface ReinforcementTrigger {

    /**
     * Examine the sim and post zero or more new requests to {@code out}.
     * Called on the service's slow-tick cadence; should be cheap enough to
     * run every tick anyway (the cadence is for noise control, not cost).
     */
    void check(BattleSimulation sim, Consumer<ReinforcementRequest> out);
}
