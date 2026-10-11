package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * Where a scene hands the frames it chooses to record.
 *
 * <p>A scene calls this at its own cadence; the sink decides what to do with the
 * frame. {@link #NONE} is what the evidence task passes, so a scene played for
 * its verdicts never touches a renderer; a snapshot suite passes a sink that
 * draws the sim through the shared review renderer and assembles an animation
 * per loop.
 */
@FunctionalInterface
public interface FrameSink {

    void frame(String loopId, BattleSimulation sim, int tick, String caption);

    /**
     * Called after every tick a recording run advances, not only the ticks
     * that record a frame — the simulation's per-frame event lists describe
     * one tick, and a sink that wants to draw an event (a landing blast) has
     * to see all of them. Defaults to nothing.
     */
    default void advanced(String loopId, BattleSimulation sim, int tick) { }

    /** Discards every frame — the evidence path. */
    FrameSink NONE = (loopId, sim, tick, caption) -> { };
}
