package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * Seam for the hull duel over Black Sluice: the Greyhook lifting alone from
 * her cradle to fight Dormer's lent Enforcer and Wolf to the finish. The ground chapter installs none by
 * default. When one is installed, the sequence tells it when Voss's withdrawal order goes out and
 * ticks it afterward; nothing it does is read by the ground referee, so ground victory, failure
 * and settlement never depend on whether a duel exists or how it ends.
 */
public interface SmallWarDuel {
    /** Voss has ordered Black Sluice abandoned: the Greyhook lifts to fight. Called once. */
    void withdrawalOrdered(BattleSimulation sim);

    /** Called every tick after the withdrawal order while the battle runs. */
    default void advance(BattleSimulation sim) { }

    /** What actually happened overhead, for the debrief; null while nothing has been observed. */
    default String outcomeSummary() { return null; }
}
