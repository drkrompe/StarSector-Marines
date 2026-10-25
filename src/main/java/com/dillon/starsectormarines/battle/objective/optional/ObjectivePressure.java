package com.dillon.starsectormarines.battle.objective.optional;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * What an objective brings against itself ({@code set-piece-blocks.md}, Lanes and pressure): an enemy of
 * its own, paced to the objective, rather than a mission enemy taught about it. The objective says where
 * it stands each advance ({@link Beat}); the pressure decides what that calls for. Ended once, whichever
 * way the objective ends.
 */
public interface ObjectivePressure {

    /** An objective with nothing brought against it. */
    ObjectivePressure NONE = new ObjectivePressure() {
        @Override public void advance(BattleSimulation sim, Beat beat, int tick) { }
        @Override public void end(BattleSimulation sim) { }
    };

    /**
     * Where the objective stands this tick: how far through it is, from 0 to 1; whether it is in its final
     * stretch (the truck set off, the last party out of the hall); and, in that stretch, the cell everything
     * the pressure owns is sent to, or null to keep them on their lanes.
     */
    record Beat(float progress, boolean finalStretch, int[] rushTo) {

        /** Under way, {@code progress} of the way through. */
        public static Beat underWay(float progress) {
            return new Beat(progress, false, null);
        }

        /** In the final stretch, with everything sent to {@code rushTo} (null keeps the lanes). */
        public static Beat last(int[] rushTo) {
            return new Beat(1f, true, rushTo);
        }
    }

    /** One step, after the objective has moved on this tick. */
    void advance(BattleSimulation sim, Beat beat, int tick);

    /** The objective is over: whatever the pressure still holds goes back to the mission. */
    void end(BattleSimulation sim);
}
