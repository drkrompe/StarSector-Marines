package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.perception.NoiseKind;

import java.util.function.LongPredicate;

/** Test-fixture access to the production belief publication boundary. */
public final class SquadBeliefTestAccess {

    private SquadBeliefTestAccess() {}

    public static void observeDirect(Squad squad, long hostile,
                                     int cellX, int cellY, int simTick) {
        squad.observeDirectContact(hostile, cellX, cellY, simTick);
        squad.publishBeliefSnapshot();
    }

    /** Records one localized hostile noise, the anonymous investigation cue. */
    public static void observeAudible(Squad squad, int cellX, int cellY, int simTick,
                                      float confidence, NoiseKind kind) {
        squad.observeAudibleBearing(cellX, cellY, simTick, confidence, 0L, kind);
        squad.publishBeliefSnapshot();
    }

    /** Runs one belief-ageing pass under {@code stillResolves} and republishes. */
    public static void ageBeliefs(Squad squad, float dt, int simTick,
                                  LongPredicate stillResolves) {
        squad.beginBeliefTick(dt, simTick, stillResolves);
        squad.publishBeliefSnapshot();
    }

    public static void forgetAll(Squad squad, int simTick) {
        squad.beginBeliefTick(0f, simTick, ignored -> false);
        squad.publishBeliefSnapshot();
    }
}
