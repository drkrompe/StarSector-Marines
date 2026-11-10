package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.battle.turret.FluxPool;
import com.dillon.starsectormarines.battle.turret.FluxShieldSpec;
import com.dillon.starsectormarines.battle.turret.ShieldTint;
import com.dillon.starsectormarines.battle.turret.TurretShieldService;

/**
 * A hull's shield emitter, the mech emitter's rule on a vehicle ({@code mech-lines.md}, the high-tech row; the
 * mech's is {@code MechShield}): a frontal flux shield with no capacitors of its own, run on the hull's one flux
 * reservoir ({@link HullReservoir}) through the hardpoint shield's rules ({@code FluxShield#pooled}). Every round it
 * stops and every second it is held charges the pool the hull's guns fire from; it folds while the pool vents, and a
 * full pool overloads the hull, shield down and guns silent while it vents to empty. A hull carries one when its type
 * says so ({@link VehicleType#shieldSpec}): Tri-Tachyon's heavy skimmer.
 *
 * <p>Every figure is first-pass and UNTUNED, for the owner's playtest.
 */
public final class HullShield {

    private HullShield() { }

    /** The frontal emitter: a third of a circle round the hull, swinging to what threatens it. */
    public static final FluxShieldSpec FRONTAL = new FluxShieldSpec(
            2.0f,              // radius, cells: just clear of the heavy skimmer's nose and flanks
            120f,              // arc
            MechFlux.CAPACITY, // not read: a pooled shield's capacity is its pool's
            0f,                // not read: the pool sheds its own flux
            2f,                // upkeep a second, well under the stock reservoir's dissipation
            0.6f,              // flux per blocked point of damage
            0f,                // not read: the guns charge the pool themselves
            0.5f,              // raise
            0.3f,              // lower
            5f,                // overload: shield down, guns silent, while the pool vents to empty
            120f,              // turn, degrees a second: a heavier emitter than a mech's
            2.5f);             // held this long after the last threat

    /** High-tech blue, as a high-tech emitter is drawn everywhere. */
    public static final ShieldTint TINT = ShieldTint.BLUE;

    /**
     * Fits hull {@code id} of {@code type} with its shield on its {@code reservoir}, its arc first facing the hull's
     * nose ({@code bodyFacingDegrees}, 0 along +Y, as a {@link GroundBody} faces). True when it fitted one; false for a
     * hull whose type carries no emitter, a hull with no reservoir, and a hull already fitted, which keeps its shield.
     */
    public static boolean fit(TurretShieldService shields, long id, VehicleType type, float bodyFacingDegrees,
                              FluxPool reservoir) {
        FluxShieldSpec spec = type == null ? null : type.shieldSpec();
        if (spec == null || reservoir == null || shields.fitted(id) != null) return false;
        shields.fitPooled(id, spec, TINT, bodyFacingDegrees + 90f, reservoir);
        return true;
    }
}
