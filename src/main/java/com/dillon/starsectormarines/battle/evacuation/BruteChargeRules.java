package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.StaggerService.Cause;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

/**
 * The numbers and the pure decisions of a Brute's charge, its blows and what they do to what they hit
 * ({@code colony-breach-nouns.md}, Brute). Every number is a starting value to feel out in play.
 */
final class BruteChargeRules {

    /** Cells per second at an adult's charge; a stage's own is {@link SwarmStageStats#bruteChargeSpeed}. */
    static final float CHARGE_SPEED = 4.0f;
    /** A target nearer than this is struck, not charged. */
    static final float MIN_BAND = 3f;
    /** A target further than this is walked toward. */
    static final float MAX_BAND = 8f;
    /** Seconds from the end of one charge to the next. */
    static final float COOLDOWN = 8f;
    /** Seconds the Brute stands head-down before it goes: the player's warning. */
    static final float TELL = .35f;
    /** Degrees a second a running Brute can turn toward its target. */
    static final float TURN_RATE = 30f;
    /** Cells a charge runs before it gives out. */
    static final float MAX_RUN = 10f;
    /** Seconds the Brute stands after a charge before it walks on. */
    static final float PULL_UP = .5f;
    /** The charge's hit against the Brute's ordinary blow. */
    static final float DAMAGE_SCALE = 1.5f;
    /** A light wall's share of a charge: one blow at double the walking ram. */
    static final int WALL_BLOW = 50;
    /** Bodies a charge goes through; the one after stops it. */
    static final int MAX_BODIES = 2;
    /** Pace kept after each body or prop it goes through. */
    static final float BODY_SLOW = .75f;
    static final float KNOCKDOWN = 1f;
    static final float THROW = 1.2f;
    /**
     * A Brute's mass in tonnes, against {@link com.dillon.starsectormarines.battle.vehicle.VehicleType#massTonnes}:
     * what a hull it runs into takes from it is a momentum exchange
     * ({@link com.dillon.starsectormarines.battle.vehicle.HullImpact}), not a fixed shove.
     */
    static final float MASS = 3f;
    /** How much a charge bounces off a hull: 0 is a dead stop against it, 1 a perfect bounce. */
    static final float RESTITUTION = .2f;
    /** Impulse (tonne-cells a second) a Brute's plate and bulk take without harm; damage per unit past it. */
    static final float SAFE_IMPULSE = 5f;
    static final float RECOIL_DAMAGE = 1f;
    /** Decelerations, cells a second squared, of a thrown body sliding to rest and of a Brute's rebound. */
    static final float BODY_SLIDE = 9f;
    static final float BRUTE_SLIDE = 8f;
    /**
     * How far a Brute's blow reaches, from its centre to the surface of what it strikes: its head, about a
     * cell. Measured to a hull's footprint, not to a circle about its centre.
     */
    static final float BLOW_REACH = 1.0f;
    /** The speed a blow drives the Brute's weight into what it strikes, cells a second: what a hull or mech takes. */
    static final float BLOW_SPEED = 2.5f;
    /** A mech's tonnes per unit of its variant's relative mass, and how hard its feet hold a slide. */
    static final float MECH_TONNES = 10f;
    static final float MECH_SLIDE = 4f;
    /** A trampled body's time down, how far it is bowled, and the ground under the hooves (cells). */
    static final float TRAMPLE_HOLD = .6f;
    static final float TRAMPLE_BOWL = .4f;
    static final float TRAMPLE_RADIUS = .75f;
    /** What a body beside the one trampled takes of the blow. */
    static final float TRAMPLE_SPLASH = .5f;
    static final float STALL_LIGHT = .8f;
    static final float STALL_MEDIUM = .4f;
    static final float STUN = 1f;
    static final float MECH_STAGGER = .6f;
    /** Squad mates this close to a thrown body take a near-miss drain. */
    static final float FEAR_RADIUS = 2f;
    /** Mechs lighter than this are light; up to {@link #HEAVY_MECH_MASS} medium; heavy from there. */
    static final float MEDIUM_MECH_MASS = 1f;
    static final float HEAVY_MECH_MASS = 1.5f;

    private BruteChargeRules() {}

    /** What a charge can run into. */
    enum Kind { INFANTRY, EMPLACEMENT, LIGHT_VEHICLE, MEDIUM_VEHICLE, HEAVY_VEHICLE, LIGHT_MECH, MEDIUM_MECH, HEAVY_MECH }

    /**
     * What a charge's hit does to a body of one {@link Kind}: how far it is thrown sideways, how long it
     * can't act, how long the Brute is stunned, whether the blow is aimed at the legs, and whether the
     * charge goes on through it. How far a hull moves is not here: that is the momentum the two exchange.
     */
    record Outcome(float throwCells, float holdSeconds, float bruteStunSeconds, boolean legs, boolean goesOn) {}

    /**
     * What a charge's hit does at {@code stage}: the weight class the Brute meets is shifted by its
     * size ({@link #felt}), and the throw and knockdown it deals infantry scale with its momentum.
     */
    static Outcome outcome(Kind kind, SwarmStage stage) {
        Outcome base = outcome(felt(kind, stage));
        if (kind != Kind.INFANTRY) return base;
        float m = SwarmStageStats.bruteMomentumRatio(stage);
        return new Outcome(base.throwCells() * m, base.holdSeconds() * m, base.bruteStunSeconds(), base.legs(),
                base.goesOn());
    }

    /**
     * The weight class a Brute of {@code stage} meets in {@code kind}: a whelp finds every hull and mech one
     * class heavier (it rebounds off an APC and is stunned), an elder one class lighter (it staggers a heavy
     * mech an adult only rocks). Infantry and emplacements are what they are.
     */
    static Kind felt(Kind kind, SwarmStage stage) {
        int shift = switch (SwarmStage.of(stage)) {
            case WHELP -> 1;
            case ADULT -> 0;
            case ELDER -> -1;
        };
        if (shift == 0) return kind;
        return switch (kind) {
            case INFANTRY, EMPLACEMENT -> kind;
            case LIGHT_VEHICLE, MEDIUM_VEHICLE, HEAVY_VEHICLE -> shiftWithin(kind, shift,
                    Kind.LIGHT_VEHICLE, Kind.HEAVY_VEHICLE);
            case LIGHT_MECH, MEDIUM_MECH, HEAVY_MECH -> shiftWithin(kind, shift, Kind.LIGHT_MECH, Kind.HEAVY_MECH);
        };
    }

    private static Kind shiftWithin(Kind kind, int shift, Kind lightest, Kind heaviest) {
        int i = Math.max(lightest.ordinal(), Math.min(heaviest.ordinal(), kind.ordinal() + shift));
        return Kind.values()[i];
    }

    /** Bodies a charge at {@code stage} goes through: a whelp's stops on the first, an elder's goes through three. */
    static int maxBodies(SwarmStage stage) {
        return switch (SwarmStage.of(stage)) {
            case WHELP -> 0;
            case ADULT -> MAX_BODIES;
            case ELDER -> 3;
        };
    }

    /**
     * What a charge at {@code stage} does to a light wall: the adult's {@link #WALL_BLOW} scaled by momentum.
     * No stage breaks a fortified wall; that is a law of which walls a charge damages, not of this number.
     */
    static int wallBlow(SwarmStage stage) {
        return Math.round(WALL_BLOW * SwarmStageStats.bruteMomentumRatio(stage));
    }

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
        return distance >= MIN_BAND && distance <= MAX_BAND && lineOpen && now >= readyTick;
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

    /**
     * The velocity change a body of {@code mass} takes, struck head-on by a Brute closing at {@code speed}:
     * momentum conserved between two point masses, bouncing by {@link #RESTITUTION}.
     */
    static float pointPush(float speed, float mass) {
        return pointPush(speed, MASS, mass);
    }

    /** {@link #pointPush(float, float)} for a Brute of {@code bruteMass} tonnes. */
    static float pointPush(float speed, float bruteMass, float mass) {
        return (1f + RESTITUTION) * speed / (1f / bruteMass + 1f / mass) / mass;
    }

    /** Speed a body must leave at to slide {@code cells} against a deceleration of {@code decel}. */
    static float launchSpeed(float cells, float decel) {
        return (float) Math.sqrt(2f * decel * cells);
    }

    static int ticks(float seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }

}
