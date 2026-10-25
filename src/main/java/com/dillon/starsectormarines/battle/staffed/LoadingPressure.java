package com.dillon.starsectormarines.battle.staffed;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * What a loading brings against itself ({@code set-piece-blocks.md}, Lanes and pressure): an enemy of
 * its own, paced to the loading, rather than a mission enemy taught about the objective. Advanced with
 * the loading on every board advance, and ended once whichever way the objective ends.
 */
public interface LoadingPressure {

    /** A loading with nothing brought against it. */
    LoadingPressure NONE = new LoadingPressure() {
        @Override public void advance(BattleSimulation sim, Loading loading, int tick) { }
        @Override public void end(BattleSimulation sim) { }
    };

    /** One step, after the loading has moved on this tick. */
    void advance(BattleSimulation sim, Loading loading, int tick);

    /** The objective is over: whatever the pressure still holds goes back to the mission. */
    void end(BattleSimulation sim);
}
