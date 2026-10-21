package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.StaggerService.Cause;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

/**
 * The numbers and the pure decisions of a Brute's charge ({@code colony-breach-nouns.md}, Brute). Every number is
 * a starting value to feel out, so each is a {@code -Dbattle.brute.*} switch: a feel pass is a command
 * line, not an edit.
 */
final class BruteChargeRules {

    /** {@code -Dbattle.brute.charge=false} keeps the Brute walking: the control run. */
    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("battle.brute.charge", "true"));
    /** Cells per second at a charge; the walk is the unit type's own speed. */
    static final float CHARGE_SPEED = f("chargeSpeed", 4.0f);
    /** A target nearer than this is struck, not charged. */
    static final float MIN_BAND = f("chargeMinBand", 3f);
    /** A target further than this is walked toward. */
    static final float MAX_BAND = f("chargeMaxBand", 8f);
    /** Seconds from the end of one charge to the next. */
    static final float COOLDOWN = f("chargeCooldown", 8f);
    /** Seconds the Brute stands head-down before it goes: the player's warning. */
    static final float TELL = f("chargeTell", .35f);
    /** Degrees a second a running Brute can turn toward its target. */
    static final float TURN_RATE = f("chargeTurnRate", 30f);
    /** Cells a charge runs before it gives out. */
    static final float MAX_RUN = f("chargeMaxRun", 10f);
    /** Seconds the Brute stands after a charge before it walks on. */
    static final float PULL_UP = f("chargePullUp", .5f);
    /** The charge's hit against the Brute's ordinary blow. */
    static final float DAMAGE_SCALE = f("chargeDamageScale", 1.5f);
    /** A light wall's share of a charge: one blow at double the walking ram. */
    static final int WALL_BLOW = Integer.getInteger("battle.brute.chargeWallBlow", 50);
    /** Bodies a charge goes through; the one after stops it. */
    static final int MAX_BODIES = Integer.getInteger("battle.brute.chargeMaxBodies", 2);
    /** Pace kept after each body or prop it goes through. */
    static final float BODY_SLOW = f("chargeBodySlow", .75f);
    static final float KNOCKDOWN = f("knockdown", 1f);
    static final float THROW = f("throw", 1.2f);
    /**
     * A Brute's mass in tonnes, against {@link com.dillon.starsectormarines.battle.vehicle.VehicleType#massTonnes}:
     * what a hull it runs into takes from it is a momentum exchange
     * ({@link com.dillon.starsectormarines.battle.vehicle.HullImpact}), not a fixed shove.
     */
    static final float MASS = f("mass", 3f);
    /** How much a charge bounces off a hull: 0 is a dead stop against it, 1 a perfect bounce. */
    static final float RESTITUTION = f("restitution", .2f);
    /** Impulse (tonne-cells a second) a Brute's plate and bulk take without harm; damage per unit past it. */
    static final float SAFE_IMPULSE = f("safeImpulse", 5f);
    static final float RECOIL_DAMAGE = f("recoilDamage", 1f);
    /** Decelerations, cells a second squared, of a thrown body sliding to rest and of a Brute's rebound. */
    static final float BODY_SLIDE = f("bodySlide", 9f);
    static final float BRUTE_SLIDE = f("bruteSlide", 8f);
    /** {@code -Dbattle.brute.trample=false} keeps the Brute's blow on infantry an ordinary hit: the control. */
    static final boolean TRAMPLE = Boolean.parseBoolean(System.getProperty("battle.brute.trample", "true"));
    /** A trampled body's time down, how far it is bowled, and the ground under the hooves (cells). */
    static final float TRAMPLE_HOLD = f("trampleHold", .6f);
    static final float TRAMPLE_BOWL = f("trampleBowl", .4f);
    static final float TRAMPLE_RADIUS = f("trampleRadius", .75f);
    /** What a body beside the one trampled takes of the blow. */
    static final float TRAMPLE_SPLASH = f("trampleSplash", .5f);
    static final float STALL_LIGHT = f("stallLight", .8f);
    static final float STALL_MEDIUM = f("stallMedium", .4f);
    static final float STUN = f("stun", 1f);
    static final float MECH_STAGGER = f("mechStagger", .6f);
    /** Squad mates this close to a thrown body take a near-miss drain. */
    static final float FEAR_RADIUS = f("fearRadius", 2f);
    /** Mechs lighter than this are light; up to {@link #HEAVY_MECH_MASS} medium; heavy from there. */
    static final float MEDIUM_MECH_MASS = f("mediumMechMass", 1f);
    static final float HEAVY_MECH_MASS = f("heavyMechMass", 1.5f);

    private BruteChargeRules() {}

    /** What a charge can run into. */
    enum Kind { INFANTRY, EMPLACEMENT, LIGHT_VEHICLE, MEDIUM_VEHICLE, HEAVY_VEHICLE, LIGHT_MECH, MEDIUM_MECH, HEAVY_MECH }

    /**
     * What a charge's hit does to a body of one {@link Kind}: how far it is thrown sideways, how long it
     * can't act, how long the Brute is stunned, whether the blow is aimed at the legs, and whether the
     * charge goes on through it. How far a hull moves is not here: that is the momentum the two exchange.
     */
    record Outcome(float throwCells, float holdSeconds, float bruteStunSeconds, boolean legs, boolean goesOn) {}

    static Outcome outcome(Kind kind) {
        return switch (kind) {
            case INFANTRY -> new Outcome(THROW, KNOCKDOWN, 0f, false, true);
            case EMPLACEMENT -> new Outcome(0f, 0f, 0f, false, false);
            case LIGHT_VEHICLE -> new Outcome(0f, STALL_LIGHT, 0f, false, false);
            case MEDIUM_VEHICLE -> new Outcome(0f, STALL_MEDIUM, 0f, false, false);
            case HEAVY_VEHICLE -> new Outcome(0f, 0f, STUN, false, false);
            case LIGHT_MECH, MEDIUM_MECH -> new Outcome(0f, MECH_STAGGER, 0f, true, false);
            case HEAVY_MECH -> new Outcome(0f, 0f, 0f, true, false);
        };
    }

    /** What a held body of {@code kind} is shown as. */
    static Cause holdCause(Kind kind) {
        return switch (kind) {
            case INFANTRY -> Cause.KNOCKED_DOWN;
            case LIGHT_VEHICLE, MEDIUM_VEHICLE, HEAVY_VEHICLE, EMPLACEMENT -> Cause.STALLED;
            case LIGHT_MECH, MEDIUM_MECH, HEAVY_MECH -> Cause.STAGGERED;
        };
    }

    static Kind vehicleKind(VehicleType.Weight weight) {
        return switch (weight) {
            case LIGHT -> Kind.LIGHT_VEHICLE;
            case MEDIUM -> Kind.MEDIUM_VEHICLE;
            case HEAVY -> Kind.HEAVY_VEHICLE;
        };
    }

    static Kind mechKind(float relativeMass) {
        if (relativeMass < MEDIUM_MECH_MASS) return Kind.LIGHT_MECH;
        return relativeMass < HEAVY_MECH_MASS ? Kind.MEDIUM_MECH : Kind.HEAVY_MECH;
    }

    /** Whether a Brute charges: a target in the band, an open line to it, and its cooldown spent. */
    static boolean shouldCharge(float distance, boolean lineOpen, int now, int readyTick) {
        return ENABLED && distance >= MIN_BAND && distance <= MAX_BAND && lineOpen && now >= readyTick;
    }

    /**
     * Unit direction a body at {@code (relX, relY)} from the Brute is thrown, the Brute running along
     * the unit heading {@code (hx, hy)}: sideways, to the side of the line it stands on, and a little
     * onward. A body dead on the line goes to the heading's left, so the result never depends on a tie.
     */
    static float[] throwDirection(float hx, float hy, float relX, float relY) {
        float cross = hx * relY - hy * relX;
        float sx = cross >= 0f ? -hy : hy;
        float sy = cross >= 0f ? hx : -hx;
        float x = sx + .4f * hx, y = sy + .4f * hy;
        float len = (float) Math.hypot(x, y);
        return new float[]{x / len, y / len};
    }

    /** {@code heading} turned toward {@code desired} by at most {@code maxStep} degrees. */
    static float turnToward(float heading, float desired, float maxStep) {
        float delta = ((desired - heading) % 360f + 540f) % 360f - 180f;
        return heading + Math.max(-maxStep, Math.min(maxStep, delta));
    }

    /** What a Brute takes from an impulse it could not shed: nothing up to {@link #SAFE_IMPULSE}. */
    static float recoilDamage(float impulse) {
        return Math.max(0f, impulse - SAFE_IMPULSE) * RECOIL_DAMAGE;
    }

    /** Speed a body must leave at to slide {@code cells} against a deceleration of {@code decel}. */
    static float launchSpeed(float cells, float decel) {
        return (float) Math.sqrt(2f * decel * cells);
    }

    static int ticks(float seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }

    private static float f(String key, float fallback) {
        return Float.parseFloat(System.getProperty("battle.brute." + key, Float.toString(fallback)));
    }
}
