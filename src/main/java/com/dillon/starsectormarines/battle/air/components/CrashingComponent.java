package com.dillon.starsectormarines.battle.air.components;

import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * Component marking an entity as falling out of the sky after death — a
 * shot-down (or hub-cascade-killed) air unit spiralling to the ground before it
 * settles into a smoking wreck. Its <em>presence</em> is the "is crashing"
 * state: attach it on death, and the entity is crashing; remove it on impact,
 * and the entity is done. There is no separate {@code started}/{@code crashed}
 * flag — the store membership is the flag.
 *
 * <p>Composes with the entity's {@link AirBody} (the position + facing carrier)
 * rather than re-declaring position: the crash system reads {@link #body} for
 * the wreck site and spins {@code body.facingDegrees} as the entity tumbles.
 * Carrying the body reference (not a snapshot) keeps the falling sprite tracking
 * the same physical position the live unit had.
 *
 * <p>Carries its own tuning ({@link #timer}, {@link #spinDegPerSec}) seeded at
 * attach time, so the crash system stays entity-agnostic — it never has to know
 * a drone from a future fighter, only that the entity has this component.
 */
public final class CrashingComponent {

    /** The falling entity's body — read for the wreck position, mutated to spin the facing. */
    public final AirBody body;

    /** Sim-seconds remaining until ground impact. Counts down each tick; impact at &le; 0. */
    public float timer;

    /** Facing spin applied while falling, deg/sec — the out-of-control tumble. */
    public final float spinDegPerSec;

    public CrashingComponent(AirBody body, float timer, float spinDegPerSec) {
        this.body = body;
        this.timer = timer;
        this.spinDegPerSec = spinDegPerSec;
    }
}
