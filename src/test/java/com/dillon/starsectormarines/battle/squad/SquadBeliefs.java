package com.dillon.starsectormarines.battle.squad;

/** Plants a squad's belief for tests outside this package, which cannot reach the alert system's writers. */
public final class SquadBeliefs {

    private SquadBeliefs() { }

    /** {@code squad} has just seen {@code unitId} at ({@code cellX}, {@code cellY}), published as of {@code simTick}. */
    public static void sighted(Squad squad, long unitId, int cellX, int cellY, int simTick) {
        squad.observeDirectContact(unitId, cellX, cellY, simTick);
        squad.publishBeliefSnapshot();
    }
}
