package com.dillon.starsectormarines.battle.fx;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * Per-tick smoke emitter parked at a destroyed turret's mount cell. Pushed
 * onto {@link BattleSimulation} when a turret demolishes; the sim ages
 * {@link #remainingLifetime} and uses {@link #nextPuffTimer} to schedule
 * periodic smoke-puff events the renderer drains into the impact FX engine.
 *
 * <p>Cools visually as it ages — puff radius scales with the remaining-lifetime
 * fraction so a fresh wreck billows hard and an old one wisps. Removed from
 * the sim list when remainingLifetime hits zero.
 */
public final class SmokingWreck {

    public final int cellX;
    public final int cellY;
    public final float totalLifetime;
    public float remainingLifetime;
    /** Sim-seconds until the next smoke puff. Reset on each emission with mild jitter. */
    public float nextPuffTimer;
    /** Sim-seconds until the next fire burst. Independent of {@link #nextPuffTimer} so fire and smoke interleave naturally rather than emitting in paired ticks. */
    public float nextFireTimer;

    public SmokingWreck(int cellX, int cellY, float totalLifetime, float firstPuffDelay) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.totalLifetime = totalLifetime;
        this.remainingLifetime = totalLifetime;
        this.nextPuffTimer = firstPuffDelay;
        this.nextFireTimer = 0.05f;
    }
}
