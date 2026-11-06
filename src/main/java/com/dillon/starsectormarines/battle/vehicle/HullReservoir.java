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
    /** The double capacitor bank's frame (`MechInternal.DOUBLE_CAPACITOR_BANK`): the military bank's reservoir in 2x1. */
    public static final Frame DOUBLE_BANK = new Frame(120f, 7.5f);
    /** The military capacitor bank's frame (`MechInternal.MILITARY_CAPACITOR_BANK`), 2x2 on a rack. */
    public static final Frame MILITARY_BANK = new Frame(120f, 8f);

    /** What one auxiliary vent array adds to a hull's dissipation (`MechInternal.AUXILIARY_VENT_ARRAY`). */
    public static final float VENT_ARRAY_DISSIPATION = 1f;
    /**
     * The laser tank's stock bank and vent arrays: a bigger stock fit so it sustains its PL-3, whose flux
     * cost is the mech part's and is not the hull's to change (owner, 2026-10-30).
     */
    static final Frame LASER_TANK_STOCK_BANK = DOUBLE_BANK;
    static final int LASER_TANK_STOCK_VENT_ARRAYS = 2;
    /**
     * What the laser tank's own power plant, in the shell stowage's room, sheds a second on top of its rack's parts: with
     * its stock bank and vent arrays, enough to pay its PL-3's 38 flux a burst on every two-second cycle (owner,
     * 2026-11-05: the burst laser tank was still bad; it fired a burst every four seconds on flux alone).
     */
    static final float LASER_TANK_PLANT_DISSIPATION = 10f;

    /** What {@code type}'s hull sheds a second of its own, beside its rack's parts: the laser tank's plant, none elsewhere. */
    public static float plantDissipation(VehicleType type) {
        return type == VehicleType.LASER_TANK ? LASER_TANK_PLANT_DISSIPATION : 0f;
    }

    /** The bank {@code type} is built with: the laser tank's own, the factory bank on every other armed hull. */
    public static Frame stockBank(VehicleType type) {
        if (type == null || !type.hasTurretWeapon()) return null;
        return type == VehicleType.LASER_TANK ? LASER_TANK_STOCK_BANK : FACTORY_BANK;
    }

    /** Vent arrays {@code type} is built with beside its bank: the laser tank's, none on any other hull. */
    public static int stockVentArrays(VehicleType type) {
        return type == VehicleType.LASER_TANK ? LASER_TANK_STOCK_VENT_ARRAYS : 0;
    }

    /**
     * The frame a hull of {@code type} is set down with in its stock fit: its bank ({@link #stockBank}), each
     * stock vent array's dissipation and its own plant's ({@link #plantDissipation}) on top; null for an unarmed
     * hull, which needs none.
     */
    public static Frame stockFrame(VehicleType type) {
        Frame bank = stockBank(type);
        if (bank == null) return null;
        float extra = stockVentArrays(type) * VENT_ARRAY_DISSIPATION + plantDissipation(type);
        if (extra == 0f) return bank;
        return new Frame(bank.capacity(), bank.dissipationPerSecond() + extra);
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
