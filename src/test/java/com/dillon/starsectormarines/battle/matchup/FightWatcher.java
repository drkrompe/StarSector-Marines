package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/** What watches a matchup fight as it plays: a recording, or a look study that keeps only the frames it wants. */
interface FightWatcher {

    /** Called once per tick before the sim advances. {@code status} is the caption's live reading. */
    void observe(BattleSimulation sim, int tick, boolean damageDealt, String status, long[][] sides);

    /** Called once per tick after the sim advanced, so shots fired this tick can be drawn as impacts. */
    void advanced(BattleSimulation sim);

    /** The final state. */
    void finish(BattleSimulation sim, int tick, String status, long[][] sides);
}
