package com.dillon.starsectormarines.battle.vehicle;

/**
 * The engine a company vehicle is set down with (mechs-nouns.md, a vehicle is a mech for parts): the same
 * three engines a mech runs, as the one number the drop reads. The stock engine is the hull's own drive
 * and changes nothing; the campaign layer maps a fitted engine part to one of these, so the battle never
 * sees an inventory object. A vehicle has no output budget, mass or flux reservoir, so only the speed of
 * the engine maps: the factor is on the hull's own top speed and acceleration, so a heavy hull stays
 * slower than a light one whatever it is fitted with.
 */
public enum VehicleEngine {
    /** The hull's own drive. */
    STOCK(1f),
    /** An uprated engine: a tenth more top speed and acceleration. */
    UPRATED(1.10f),
    /** A military engine: a fifth more top speed and acceleration. */
    MILITARY(1.20f);

    private final float speedFactor;

    VehicleEngine(float speedFactor) {
        this.speedFactor = speedFactor;
    }

    /** What this engine multiplies the hull's top speed and acceleration by. */
    public float speedFactor() { return speedFactor; }
}
