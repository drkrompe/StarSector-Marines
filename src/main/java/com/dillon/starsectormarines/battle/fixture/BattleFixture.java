package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * Versioned construction facts for rebuilding a battle through its production
 * scenario factory. A fixture reconstructs the battle at tick zero; it is not
 * a checkpoint of mutable simulation state.
 */
public interface BattleFixture {

    /** Stable discriminator written by {@link BattleFixtureJson}. */
    String kind();

    /** Builds a fresh simulation through the ordinary production setup path. */
    BattleSimulation build();
}
