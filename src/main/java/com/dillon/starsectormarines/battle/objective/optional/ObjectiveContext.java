package com.dillon.starsectormarines.battle.objective.optional;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;

/**
 * What a rolled objective may read and where it reports: the battle, the map's
 * laid layout, the battle seed, and the battle's story beats under the
 * mission's prefix ({@code "cordon."}, {@code "heart."}).
 *
 * @param sim the battle; {@code null} only where a caller has none (a unit check of the board)
 */
public record ObjectiveContext(BattleSimulation sim, LaidLayout laid, long battleSeed, String beatPrefix,
                               StoryBeatLog beats) {

    public ObjectiveContext {
        if (beats == null) throw new IllegalArgumentException("An objective context needs the battle's beat log");
        laid = laid == null ? LaidLayout.EMPTY : laid;
        beatPrefix = beatPrefix == null ? "" : beatPrefix;
    }

    /** The context over a live battle, publishing into its own beat log. */
    public static ObjectiveContext forBattle(BattleSimulation sim, LaidLayout laid, long battleSeed, String beatPrefix) {
        return new ObjectiveContext(sim, laid, battleSeed, beatPrefix, sim.getStoryBeatLog());
    }

    /** Publishes {@code prefix + beat} for the player at this tick, once per key. */
    public void publish(int tick, String beat) {
        beats.emitOnce(tick, beatPrefix + beat, StoryBeatLog.Audience.PLAYER);
    }

    /** Whether the mission's beat {@code prefix + beat} has fired. */
    public boolean hasBeat(String beat) { return beats.has(beatPrefix + beat); }
}
