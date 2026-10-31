package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/**
 * How near a hull's own gun can fire: its weapon's minimum range, or, for a gun whose round bursts, the distance at
 * which the burst would reach the hull that fired it. A crew holds a round that would land on its own hull
 * ({@code TurretFireSystem}, lane discipline), so a body inside this is not a target the gun has, and the turret's
 * aim takes one further out ({@link GroundVehicleTurretSystem}). Without it a cannon tank kept its gun on a Rav
 * clawing at its side, holding the round cooldown after cooldown while Ravs further off came on unshot.
 */
final class GunDeadZone {

    /** Cells past the burst's reach, for the round's spread and the body's step while the round is in flight. */
    static final float MARGIN = 0.5f;

    private GunDeadZone() {}

    /**
     * The dead zone's reach from a mount {@code mountOffset} cells off the hull's centre, as the turret's aim
     * measures it: the minimum range is the mount's own, and the burst is kept off the hull wherever the mount
     * sits on it. Zero for a gun with neither.
     */
    static float fromMount(WeaponDef weapon, float hullRadius, float mountOffset) {
        return fromMount(weapon.minRange, weapon.aoeRadius, hullRadius, mountOffset);
    }

    static float fromMount(float minRange, float aoeRadius, float hullRadius, float mountOffset) {
        return Math.max(minRange, aoeRadius > 0f ? aoeRadius + hullRadius + MARGIN + mountOffset : 0f);
    }
}
