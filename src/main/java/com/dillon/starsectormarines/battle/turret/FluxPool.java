package com.dillon.starsectormarines.battle.turret;

/**
 * A machine's one flux pool, as a pooled shield ({@link FluxShield#pooled}) runs on it: its guns fire from it too, and
 * it sheds flux, vents and overloads by its own rules (a mech's is {@code MechFlux}).
 */
public interface FluxPool {

    /** Flux the pool holds now. */
    float value();

    /** Flux the pool holds when full. */
    float capacity();

    /** Whether the guns' heat has the pool venting: no shield can be held on it. */
    boolean venting();

    /** Whether the pool is overloaded: shield down, every gun silent. */
    boolean overloaded();

    /** Share of the overload still to run, {@code [0, 1]}. */
    float overloadShare();

    /** Adds flux that is never refused, a blocked round or a held shield; true when it filled the pool. */
    boolean charge(float amount);

    /** Overloads the pool for {@code seconds}. */
    void overload(float seconds);
}
