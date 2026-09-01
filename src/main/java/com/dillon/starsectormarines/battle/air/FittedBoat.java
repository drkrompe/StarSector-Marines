package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;

import java.util.Objects;

/**
 * The battle-facing airframe of one of the company's boats: a
 * {@link ShuttleType} pattern with the fittings a yard has put on it.
 *
 * <p>Frozen at launch and read nowhere else. The campaign side owns a
 * {@code CampaignBoat} whose fittings the player can change; this is what that
 * boat is for the length of one battle, so a refit mid-mission is not a thing
 * the sim has to have an opinion about. It is what a {@link ShuttleAssignment}
 * carries and what {@code AirSystem} seeds an air entity's identity, durability
 * and handling from.
 *
 * <p>Lives beside the pattern rather than beside the fitting because it is a
 * battle noun: {@code AirSystem}, {@code AirSteeringSystem} and the fixture
 * codec all read it, and none of them should have to know a company owns
 * anything. It reads {@link BoatFitting} across the seam the other way, which
 * is the direction {@code battle} already reads {@code marine}'s authored defs
 * in — the equipment, integral-system and deployable specs all arrive that way.
 *
 * <p><b>Only the two numbers a fitting is about are touched.</b> Plating scales
 * the hull; the drive scales forward speed and both accelerations. Turn rate,
 * damping, seats, hardpoints, sprite and hull id are the pattern's and are
 * delegated untouched — a better boat is faster and tougher, not a different
 * aircraft. The deboard cadence and the deck turnaround are deliberately not
 * here either: the rotation is a fact about hands, and a fitting does not buy
 * more of them.
 */
public final class FittedBoat implements Airframe, AirHandling {

    private final ShuttleType pattern;
    private final BoatFitting plating;
    private final BoatFitting drive;
    private final String boatId;

    public FittedBoat(ShuttleType pattern, BoatFitting plating, BoatFitting drive) {
        this(pattern, plating, drive, null);
    }

    public FittedBoat(ShuttleType pattern, BoatFitting plating, BoatFitting drive,
                      String boatId) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.plating = requireSlot(plating, BoatFittingSlot.PLATING);
        this.drive = requireSlot(drive, BoatFittingSlot.DRIVE);
        this.boatId = boatId;
    }

    /** The pattern as it leaves the yard, which is every boat a hull comes with. */
    public static FittedBoat standard(ShuttleType pattern) {
        return new FittedBoat(pattern,
                BoatFitting.standard(BoatFittingSlot.PLATING),
                BoatFitting.standard(BoatFittingSlot.DRIVE));
    }

    /** The airframe underneath the fit — what kind of boat this is. */
    public ShuttleType pattern() { return pattern; }

    public BoatFitting plating() { return plating; }

    public BoatFitting drive() { return drive; }

    /**
     * The campaign boat this frame was frozen from, or null when nothing owns
     * it — an employer's craft, a fixture's bare pattern, a standard fit stood
     * up for a briefing that has no deck behind it.
     *
     * <p>The one thread back across the seam, and deliberately only an id: the
     * sim has no business reading a company's roster, but a boat that burns
     * over the objective has to be nameable at resolution, and by then the
     * entity is long reaped.
     */
    public String boatId() { return boatId; }

    @Override public String spritePath()  { return pattern.spritePath(); }
    @Override public String renderHullId() { return pattern.renderHullId(); }
    @Override public AirOrdnance ordnance() { return pattern.ordnance(); }
    @Override public int hardpoints()     { return pattern.hardpoints(); }
    @Override public AirHandling flight() { return this; }

    @Override public float maxHp() { return pattern.maxHp() * plating.hullFactor(); }

    @Override public float targetRadiusCells() { return pattern.targetRadiusCells(); }

    @Override public float maxSpeed()     { return pattern.maxSpeed() * drive.speedFactor(); }
    @Override public float accel()        { return pattern.accel() * drive.accelFactor(); }
    @Override public float brakingAccel() { return pattern.brakingAccel() * drive.accelFactor(); }

    @Override public float maxTurnRateDegPerSec() { return pattern.maxTurnRateDegPerSec(); }
    @Override public float lateralDriftDamping()  { return pattern.lateralDriftDamping(); }
    @Override public float stationDamping()       { return pattern.stationDamping(); }

    /**
     * By pattern, fitting ids and owning boat, so a boat frozen twice out of the
     * same campaign state compares equal and a fixture round-trip can be
     * asserted on the manifest rather than field by field. Two of the company's
     * own boats at the same fit are still two boats, which is what the id
     * carries here — one of them can be lost.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FittedBoat boat)) return false;
        return pattern == boat.pattern
                && plating.id().equals(boat.plating.id())
                && drive.id().equals(boat.drive.id())
                && Objects.equals(boatId, boat.boatId);
    }

    @Override
    public int hashCode() {
        int result = 31 * pattern.hashCode() + plating.id().hashCode();
        result = 31 * result + drive.id().hashCode();
        return 31 * result + Objects.hashCode(boatId);
    }

    @Override
    public String toString() {
        return pattern + "[" + plating.id() + "," + drive.id()
                + (boatId != null ? "," + boatId : "") + "]";
    }

    private static BoatFitting requireSlot(BoatFitting fitting, BoatFittingSlot slot) {
        Objects.requireNonNull(fitting, "fitting");
        if (fitting.slot() != slot) {
            throw new IllegalArgumentException(fitting.id() + " is a " + fitting.slot()
                    + " fitting and cannot be installed as " + slot);
        }
        return fitting;
    }
}
