package com.dillon.starsectormarines.battle.squad;

/** Test-fixture access to the production belief publication boundary. */
public final class SquadBeliefTestAccess {

    private SquadBeliefTestAccess() {}

    public static void observeDirect(Squad squad, long hostile,
                                     int cellX, int cellY, int simTick) {
        squad.observeDirectContact(hostile, cellX, cellY, simTick);
        squad.publishBeliefSnapshot();
    }

    public static void forgetAll(Squad squad, int simTick) {
        squad.beginBeliefTick(0f, simTick, ignored -> false);
        squad.publishBeliefSnapshot();
    }
}
