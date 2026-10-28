package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.turret.FluxShieldSpec;
import com.dillon.starsectormarines.battle.turret.ShieldTint;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.marine.BoatGun;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
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
 * the hull; the drive scales forward speed and both accelerations and shortens
 * the deboard cadence, a quicker ramp; a lift cradle raises the {@link #liftRating}
 * and leaves the seats where they are; the cabin sets the {@link #seats}. Turn
 * rate, damping, hardpoints, sprite and hull id are the pattern's and are
 * delegated untouched — a better boat is faster and tougher, not a different
 * aircraft. The deck turnaround is deliberately not here: it is paid in
 * hand-seconds, and a fitting does not buy more hands.
 *
 * <p><b>One flux pool for her shield and her guns.</b> {@link #shieldSpec} is her
 * shield and its capacitors, by the rules a hardpoint's flux shield runs on
 * ({@code FluxShield}): the emitter sets the arc and the flux each blocked point
 * of damage costs, the power fitting the pool and its venting. Every round but
 * a missile's charges the same pool ({@link #fluxPerRound}), so a boat that
 * fights her way in has less shield left for the landing; a full pool overloads,
 * dropping the shield and silencing every gun.
 *
 * <p><b>Weight aboard slows her.</b> {@link #carrying} is her handling with a
 * load: a machine drags by its lift class and each marine a little, so the run
 * in is slower than the run back. A better drive wins the loaded speed back.
 */
public final class FittedBoat implements Airframe, AirHandling {

    private final ShuttleType pattern;
    /** The fitting in every slot; a slot nobody named holds its standard fitting. */
    private final Map<BoatFittingSlot, BoatFitting> fit;
    private final BoatFitting plating;
    private final BoatFitting drive;
    private final BoatFitting lift;
    private final String boatId;
    /** Turret structure per hardpoint for an air-to-ground sortie; a null entry is a bare hardpoint. */
    private final String[] guns;
    /**
     * The second gun sharing each hardpoint's turret as a pod, by turret structure; a null entry
     * is a mount with one gun. A hardpoint takes two 2x1 guns (owner pick, 2026-10-27).
     */
    private final String[] pods;

    /** A hardpoint entry naming no gun at all, where null or a missing entry is the sortie kit's own. */
    public static final String BARE = "";


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
     * @param fit the fitting in each slot; a slot it leaves out holds its standard fitting,
     *     and plating or an emitter mapped to null is none: a bare hull with no
     *     armour over it, or no shield
     * @param guns the door gun's turret structure on each hardpoint, or null
     *     (or a null entry) for the gun the sortie kit would mount there, or
     *     {@link #BARE} for no gun
     */
    public FittedBoat(ShuttleType pattern, Map<BoatFittingSlot, BoatFitting> fit, String boatId, String[] guns) {
        this(pattern, fit, boatId, guns, null);
    }

    /**
     * As {@link #FittedBoat(ShuttleType, Map, String, String[])}, with a second gun on some hardpoints.
     *
     * @param pods the turret structure of the second gun sharing each hardpoint's pod, or null
     *     (or a null or blank entry) for a mount with one gun
     */
    public FittedBoat(ShuttleType pattern, Map<BoatFittingSlot, BoatFitting> fit, String boatId, String[] guns,
                      String[] pods) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.fit = new EnumMap<>(BoatFittingSlot.class);
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            BoatFitting fitted = fit == null ? null : fit.get(slot);
            if (fitted != null) this.fit.put(slot, requireSlot(fitted, slot));
            else if (optional(slot) && fit != null && fit.containsKey(slot)) this.fit.put(slot, null);
            else this.fit.put(slot, BoatFitting.standard(slot));
        }
        this.plating = this.fit.get(BoatFittingSlot.PLATING);
        this.drive = this.fit.get(BoatFittingSlot.DRIVE);
        this.lift = this.fit.get(BoatFittingSlot.LIFT);
        this.boatId = boatId;
        String[] kit = pattern.kitFor(TurretRole.A2G);
        this.guns = new String[kit.length];
        for (int index = 0; index < kit.length; index++) {
            String fitted = guns != null && index < guns.length ? guns[index] : null;
            this.guns[index] = fitted == null ? kit[index] : fitted.isBlank() ? null : fitted;
        }
        this.pods = new String[kit.length];
        for (int index = 0; index < kit.length; index++) {
            String pod = pods != null && index < pods.length ? pods[index] : null;
            this.pods[index] = pod == null || pod.isBlank() ? null : pod;
        }
    }

    /** The pattern as it leaves the yard, which is every boat a hull comes with. */
    public static FittedBoat standard(ShuttleType pattern) {
        return new FittedBoat(pattern, Map.of(), null, null);
    }

    /** The airframe underneath the fit — what kind of boat this is. */
    public ShuttleType pattern() { return pattern; }

    /** Her plating, or null for a bare hull. */
    public BoatFitting plating() { return plating; }

    public BoatFitting drive() { return drive; }

    public BoatFitting lift() { return lift; }

    /** The fitting in any slot; null only for plating or an emitter she flies without. */
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
     * Whether this boat lifts a machine, by a cradle or as her pattern was built
     * (the Hermes lifts Light). Her seats stay: she ferries squads like any boat
     * and can instead take one machine of her lift class on a sortie.
     * Deboarding stays one kind per sortie.
     */
    public boolean cradled() {
        return liftRating() != LiftRating.NONE;
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

    /**
     * The second gun on each hardpoint, sharing its turret as a pod, for an air-to-ground sortie;
     * any other role is the pattern's, which pairs nothing.
     */
    @Override
    public String[] podKitFor(TurretRole role) {
        return role == TurretRole.A2G ? pods.clone() : null;
    }

    /** The turret structure on each hardpoint, in hardpoint order; null for a bare one. */
    public List<String> guns() { return Collections.unmodifiableList(Arrays.asList(guns.clone())); }

    /** The second gun of each hardpoint's pod, in hardpoint order; null where a mount has one gun. */
    public List<String> pods() { return Collections.unmodifiableList(Arrays.asList(pods.clone())); }
    @Override public AirHandling flight() { return this; }

    /** Her hull is the pattern's; plating is armour over it, not more of it. */
    @Override public float maxHp() { return pattern.maxHp(); }

    /** The pattern's skin scaled by her plating; none on a bare hull. */
    @Override
    public float armor() {
        return plating == null ? 0f : pattern.armor() * plating.armorFactor();
    }

    /** The cabin's seats, or the pattern's where the cabin seats fewer. */
    @Override
    public int seats() {
        return Math.max(pattern.capacity, fit.get(BoatFittingSlot.CABIN).seats());
    }

    /** The pattern's ramp, quickened by the drive. */
    @Override
    public float deboardInterval() {
        return pattern.deboardInterval * drive.deboardFactor();
    }

    /** Her shield's arc with the hull's own emitter: the Hermes's 120°. */
    static final float BASE_SHIELD_ARC = 120f;
    /** Her pool with the yard's capacitors, and what it vents a second: the vehicle mounts' scale. */
    static final float BASE_FLUX = 100f;
    static final float BASE_VENT = 8f;
    /** Upkeep while the shield is up, as a share of her venting: always under it, as a warship's is. */
    static final float UPKEEP_SHARE = 0.5f;
    /** Shield radius in cells: a Hermes's, vanilla's 57 px. */
    static final float SHIELD_RADIUS_CELLS = 2.6f;
    /** Her flux guns hold fire past this share of the pool until it vents to the lower one, the warship's marks. */
    static final float GUNS_HOLD_ABOVE = 0.85f;
    static final float GUNS_RESUME_BELOW = 0.55f;

    /**
     * Her shield and its capacitors: arc and flux per blocked damage from the emitter, pool and venting from the
     * power fitting. An omni shield, turning fast to whatever fires on her. Null with no emitter fitted: she flies
     * unshielded, and her guns have no pool to charge.
     */
    public FluxShieldSpec shieldSpec() {
        BoatFitting emitter = fit.get(BoatFittingSlot.EMITTER);
        if (emitter == null) return null;
        BoatFitting power = fit.get(BoatFittingSlot.POWER);
        float arc = emitter.shieldArcDeg() > 0f ? emitter.shieldArcDeg() : BASE_SHIELD_ARC;
        float vent = BASE_VENT * power.fluxDissipationFactor();
        return new FluxShieldSpec(SHIELD_RADIUS_CELLS, arc, BASE_FLUX * power.fluxCapacityFactor(), vent,
                UPKEEP_SHARE * vent, emitter.shieldEfficiency(), 0f, 0.4f, 0.25f, 6f, 240f, 3f,
                new FluxShieldSpec.GunDraw(1f, GUNS_HOLD_ABOVE, GUNS_RESUME_BELOW));
    }

    /** Her shield's look by its emitter's tier: low-tech orange, midline teal, high-tech blue. */
    public ShieldTint shieldTint() {
        BoatFitting emitter = fit.get(BoatFittingSlot.EMITTER);
        return switch (emitter == null ? 1 : emitter.tier()) {
            case 1 -> ShieldTint.ORANGE;
            case 2 -> ShieldTint.TEAL;
            default -> ShieldTint.BLUE;
        };
    }

    /** Flux one round of the gun {@code structureId} puts on a boat's pool; none for a missile or an unknown gun. */
    public static float fluxPerRound(String structureId) {
        BoatGun gun = BoatGun.forStructure(structureId);
        return gun == null ? 0f : gun.family().fluxPerRound();
    }

    /** Top-speed loss a machine of each lift class costs her, by {@link LiftClass} ordinal. */
    private static final float[] MACHINE_DRAG = {0.12f, 0.20f, 0.30f};
    /** Top-speed loss each marine aboard costs her. */
    static final float MARINE_DRAG = 0.01f;

    /**
     * Her top speed with {@code machine} (or null) and {@code marines} aboard, as
     * a share of her empty top speed: never below half.
     */
    public static float loadFactor(LiftClass machine, int marines) {
        float drag = (machine == null ? 0f : MACHINE_DRAG[machine.ordinal()])
                + MARINE_DRAG * Math.max(0, marines);
        return Math.max(0.5f, 1f - drag);
    }

    /**
     * The handling {@code frame} flies {@code mission} with: a boat's with what
     * is aboard her now, any other aircraft's own.
     */
    public static AirHandling handling(Airframe frame, ShuttleMission mission) {
        return frame instanceof FittedBoat boat && mission != null
                ? boat.carrying(mission.machineAboard(), mission.marinesAboard()) : frame.flight();
    }

    /** Her handling with this load aboard; herself when the load costs nothing. */
    public AirHandling carrying(LiftClass machine, int marines) {
        float factor = loadFactor(machine, marines);
        return factor >= 1f ? this : new Loaded(this, maxSpeed() * factor);
    }

    private record Loaded(AirHandling base, float maxSpeed) implements AirHandling {
        @Override public float accel() { return base.accel(); }
        @Override public float brakingAccel() { return base.brakingAccel(); }
        @Override public float maxTurnRateDegPerSec() { return base.maxTurnRateDegPerSec(); }
        @Override public float lateralDriftDamping() { return base.lateralDriftDamping(); }
        @Override public float stationDamping() { return base.stationDamping(); }
    }

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
            if (!Objects.equals(idOf(fit.get(slot)), idOf(boat.fit.get(slot)))) return false;
        }
        return Objects.equals(boatId, boat.boatId)
                && Arrays.equals(guns, boat.guns) && Arrays.equals(pods, boat.pods);
    }

    @Override
    public int hashCode() {
        int result = pattern.hashCode();
        for (BoatFittingSlot slot : BoatFittingSlot.values()) result = 31 * result + Objects.hashCode(idOf(fit.get(slot)));
        result = 31 * result + Arrays.hashCode(guns);
        result = 31 * result + Arrays.hashCode(pods);
        return 31 * result + Objects.hashCode(boatId);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(pattern + "[" + (plating == null ? "bare" : plating.id()) + "," + drive.id());
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            BoatFitting fitted = fit.get(slot);
            if (slot != BoatFittingSlot.PLATING && slot != BoatFittingSlot.DRIVE && (fitted == null || !fitted.standard())) {
                text.append(',').append(fitted == null ? "no-" + slot.name().toLowerCase(Locale.ROOT) : fitted.id());
            }
        }
        return text.append(boatId != null ? "," + boatId : "").append(']').toString();
    }

    /** A slot she can fly with nothing in: plating (a bare hull) and the emitter (no shield). */
    public static boolean optional(BoatFittingSlot slot) {
        return slot == BoatFittingSlot.PLATING || slot == BoatFittingSlot.EMITTER;
    }

    private static String idOf(BoatFitting fitting) {
        return fitting == null ? null : fitting.id();
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
