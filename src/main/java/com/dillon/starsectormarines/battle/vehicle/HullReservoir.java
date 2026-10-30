package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechFlux;

/**
 * A hull's one flux reservoir (`convoy-nouns.md`, hull flux): every mount on the hull draws its rounds' flux from it,
 * under the mech's own admission rule ({@link MechFlux}), so a machine is a machine whatever it rolls on. The frame is
 * frozen at the drop from the hull and its rack.
 */
public final class HullReservoir {

    private HullReservoir() { }

    /**
     * A hull's flux frame at the drop: what its reservoir holds and sheds a second. The campaign freezes it from the
     * capacitor bank and vent arrays on the rack (`VehicleRack`); a hull the company does not own runs its stock bank.
     */
    public record Frame(float capacity, float dissipationPerSecond) {
        public Frame {
            if (!(capacity > 0f) || !Float.isFinite(capacity) || !(dissipationPerSecond >= 0f)
                    || !Float.isFinite(dissipationPerSecond)) {
                throw new IllegalArgumentException("Invalid hull flux frame");
            }
        }
    }

    /** The factory capacitor bank's frame, the mech stock frame's (`MechInternal.FACTORY_CAPACITOR_BANK`). */
    public static final Frame FACTORY_BANK = new Frame(MechFlux.CAPACITY, MechFlux.DISSIPATION_PER_SECOND);
    /** The military capacitor bank's frame (`MechInternal.MILITARY_CAPACITOR_BANK`): the laser tank's stock bank. */
    public static final Frame MILITARY_BANK = new Frame(120f, 8f);

    /**
     * The frame a hull of {@code type} is set down with in its stock fit: the military bank on the laser tank, whose
     * lasers are flux hogs, the factory bank on every other armed hull; null for an unarmed hull, which needs none.
     */
    public static Frame stockFrame(VehicleType type) {
        if (type == null || !type.hasTurretWeapon()) return null;
        return type == VehicleType.LASER_TANK ? MILITARY_BANK : FACTORY_BANK;
    }

    /** A fresh reservoir on {@code frame}, or null for no frame. */
    public static MechFlux create(Frame frame) {
        return frame != null ? new MechFlux(frame.capacity(), frame.dissipationPerSecond()) : null;
    }

    /** Puts one reservoir on the hull's main gun and every secondary mount, so they all draw on it. */
    public static void install(GroundTurret main, MechFlux reservoir) {
        main.reservoir = reservoir;
        for (MountedGun gun : main.secondaries) gun.turret().reservoir = reservoir;
    }
}
