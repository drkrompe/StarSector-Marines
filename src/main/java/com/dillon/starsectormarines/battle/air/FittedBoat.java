package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * <p><b>Only the numbers a fitting is about are touched.</b> Plating scales
 * the hull; the drive scales forward speed and both accelerations; a lift
 * cradle raises the {@link #liftRating} and leaves the seats where they are. Turn rate,
 * damping, seats, hardpoints, sprite and hull id are the pattern's and are
 * delegated untouched — a better boat is faster and tougher, not a different
 * aircraft. The deboard cadence and the deck turnaround are deliberately not
 * here either: the rotation is a fact about hands, and a fitting does not buy
 * more of them.
 */
public final class FittedBoat implements Airframe, AirHandling {

    private final ShuttleType pattern;
    /** The fitting in every slot; a slot nobody named holds its standard fitting. */
    private final Map<BoatFittingSlot, BoatFitting> fit;
    private final BoatFitting plating;
    private final BoatFitting drive;
    private final BoatFitting lift;
    private final String boatId;
    /** Turret structure per hardpoint for an air-to-ground sortie; a null entry is the sortie kit's own gun. */
    private final String[] guns;

    public FittedBoat(ShuttleType pattern, BoatFitting plating, BoatFitting drive) {
        this(pattern, plating, drive, null);
    }

    public FittedBoat(ShuttleType pattern, BoatFitting plating, BoatFitting drive,
                      String boatId) {
        this(pattern, plating, drive, BoatFitting.STANDARD_LIFT, boatId);
    }

    public FittedBoat(ShuttleType pattern, BoatFitting plating, BoatFitting drive,
                      BoatFitting lift, String boatId) {
        this(pattern, plating, drive, lift, boatId, null);
    }

    /**
     * @param guns the door gun's turret structure on each hardpoint, or null
     *     (or a null entry) for the gun the sortie kit would mount there
     */
    public FittedBoat(ShuttleType pattern, BoatFitting plating, BoatFitting drive,
                      BoatFitting lift, String boatId, String[] guns) {
        this(pattern, Map.of(BoatFittingSlot.PLATING, requireSlot(plating, BoatFittingSlot.PLATING),
                BoatFittingSlot.DRIVE, requireSlot(drive, BoatFittingSlot.DRIVE),
                BoatFittingSlot.LIFT, requireSlot(lift, BoatFittingSlot.LIFT)), boatId, guns);
    }

    /**
     * @param fit the fitting in each slot; a slot it leaves out holds its standard fitting
     * @param guns the door gun's turret structure on each hardpoint, or null
     *     (or a null entry) for the gun the sortie kit would mount there
     */
    public FittedBoat(ShuttleType pattern, Map<BoatFittingSlot, BoatFitting> fit, String boatId, String[] guns) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.fit = new EnumMap<>(BoatFittingSlot.class);
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            BoatFitting fitted = fit == null ? null : fit.get(slot);
            this.fit.put(slot, fitted == null ? BoatFitting.standard(slot) : requireSlot(fitted, slot));
        }
        this.plating = this.fit.get(BoatFittingSlot.PLATING);
        this.drive = this.fit.get(BoatFittingSlot.DRIVE);
        this.lift = this.fit.get(BoatFittingSlot.LIFT);
        this.boatId = boatId;
        String[] kit = ShuttleType.kitFor(TurretRole.A2G, pattern.hardpoints());
        this.guns = new String[kit.length];
        for (int index = 0; index < kit.length; index++) {
            String fitted = guns != null && index < guns.length ? guns[index] : null;
            this.guns[index] = fitted != null && !fitted.isBlank() ? fitted : kit[index];
        }
    }

    /** The pattern as it leaves the yard, which is every boat a hull comes with. */
    public static FittedBoat standard(ShuttleType pattern) {
        return new FittedBoat(pattern, Map.of(), null, null);
    }

    /** The airframe underneath the fit — what kind of boat this is. */
    public ShuttleType pattern() { return pattern; }

    public BoatFitting plating() { return plating; }

    public BoatFitting drive() { return drive; }

    public BoatFitting lift() { return lift; }

    /** The fitting in any slot; never null. */
    public BoatFitting fitting(BoatFittingSlot slot) { return fit.get(slot); }

    /**
     * The heavier of the pattern's own lift and the cradle's: a cradled Buffalo
     * carries Light machines, a Valkyrie Heavy ones as built.
     */
    @Override
    public LiftRating liftRating() {
        return LiftRating.max(pattern.liftRating(), lift.liftRating());
    }

    /**
     * Whether this boat carries a cradle. Its seats stay: it ferries squads like
     * any boat and can instead take one machine of its lift class on a sortie.
     * Deboarding stays one kind per sortie.
     */
    public boolean cradled() {
        return lift.cradle();
    }

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

    /**
     * The door guns the yard put on, for an air-to-ground sortie; any other role
     * is the pattern's, which today arms nothing.
     */
    @Override
    public String[] kitFor(TurretRole role) {
        return role == TurretRole.A2G ? guns.clone() : pattern.kitFor(role);
    }

    /** The turret structure on each hardpoint, in hardpoint order. */
    public List<String> guns() { return List.of(guns); }
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
        if (pattern != boat.pattern) return false;
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            if (!fit.get(slot).id().equals(boat.fit.get(slot).id())) return false;
        }
        return Objects.equals(boatId, boat.boatId)
                && Arrays.equals(guns, boat.guns);
    }

    @Override
    public int hashCode() {
        int result = pattern.hashCode();
        for (BoatFittingSlot slot : BoatFittingSlot.values()) result = 31 * result + fit.get(slot).id().hashCode();
        result = 31 * result + Arrays.hashCode(guns);
        return 31 * result + Objects.hashCode(boatId);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(pattern + "[" + plating.id() + "," + drive.id());
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            BoatFitting fitted = fit.get(slot);
            if (slot != BoatFittingSlot.PLATING && slot != BoatFittingSlot.DRIVE && !fitted.standard()) {
                text.append(',').append(fitted.id());
            }
        }
        return text.append(boatId != null ? "," + boatId : "").append(']').toString();
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
