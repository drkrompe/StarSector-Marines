package com.dillon.starsectormarines.battle.warship;

import java.util.ArrayList;
import java.util.List;

/**
 * A warship taken to nothing, on its way down.
 *
 * <p>A destroyed hull no longer vanishes: it bursts, loses lift and falls for {@link #FALL_SECONDS}, keeping the
 * momentum it had (a hull that was hovering still slides off as its lift fails) and turning as it goes, then
 * strikes the ground and skids to rest. The strike and the skid are the crash an aircraft makes
 * ({@code AirframeCookOffSystem}), so the wreck hurts and marks whatever it lands on, whichever side that is. Once
 * at rest it stays where it lies for the rest of the battle.
 *
 * <p>A falling hull is coming down to the ground, so its body comes back to true landed scale as it falls
 * ({@link #bodyScale}, {@link LandedScale#fallFactor}): the lift run read backwards, keyed to the fall. It strikes,
 * skids and lies at that size, and the crush it does is the size it is drawn ({@link #crushRadius},
 * {@link #skidRadius}).
 *
 * <p>A larger hull brings more down with it ({@link Weight}): its strike and skid hit harder and deeper into armour and
 * structure, its magazines and reactor go up in secondary blasts along its keel as it falls and after it strikes
 * ({@link #secondariesBetween}), and it burns along its whole length once it lies still ({@link Weight#fires}). A
 * frigate's crash is the aircraft's, unchanged.
 *
 * <p>The motion is closed-form from the moment of the burst and every choice in it is a hash of the hull's serial,
 * so a wreck falls the same way on every play. The simulation reads only where it strikes and skids; how high it
 * is drawn ({@link #lift}) is presentation.
 */
public final class WarshipWreck {

    /** Seconds from the burst to the strike. */
    public static final float FALL_SECONDS = 3f;
    /** The speed, cells per second, a hull that was hovering slides off at as its lift fails. */
    static final float SLIP_SPEED = 3f;
    /** How hard the wreck slows on the ground, cells per second squared. */
    static final float SKID_DECELERATION = 6f;
    /** The fastest a falling hull turns, degrees per second. */
    static final float MAX_SPIN = 25f;
    /** Seconds between the secondary blasts that ripple down a struck hull. */
    static final float SECONDARY_GAP_SECONDS = 0.35f;

    /**
     * How hard a wreck of one hull size comes down. Draft tuning: the frigate's figures are the aircraft crash's
     * ({@code AirframeCookOffSystem}), and each size up carries more mass, plate and fuel.
     *
     * @param crush             damage of the strike's crush
     * @param skid              damage of each skid sweep
     * @param penetration       armour penetration of the strike, skid and secondaries
     * @param structure         structure damage of the strike
     * @param skidStructure     structure damage of each skid sweep
     * @param secondaries       blasts along the keel after the strike, each hurting what is near it
     * @param fallSecondaries   blasts along the keel while it falls; presentation only, nothing is under them yet
     * @param secondaryDamage   damage of each grounded secondary blast
     * @param secondaryStructure structure damage of each grounded secondary blast
     * @param fires             fires the wreck burns with along its length once it lies still; one is the centre's alone
     */
    public record Weight(float crush, float skid, float penetration, int structure, int skidStructure,
                         int secondaries, int fallSecondaries, float secondaryDamage, int secondaryStructure,
                         int fires) {

        public static Weight of(ShipClass.HullSize size) {
            return switch (size) {
                case FRIGATE -> new Weight(900f, 600f, 60f, 600, 400, 0, 0, 0f, 0, 1);
                case DESTROYER -> new Weight(1200f, 800f, 80f, 900, 600, 2, 1, 350f, 250, 2);
                case CRUISER -> new Weight(1600f, 1000f, 110f, 1400, 900, 3, 2, 450f, 400, 3);
                case CAPITAL -> new Weight(2400f, 1400f, 150f, 2400, 1500, 5, 3, 600f, 600, 4);
            };
        }
    }

    /**
     * One secondary blast: where along the keel it goes off ({@code keel}, 0 at the bow to 1 at the stern) and whether
     * the hull is on the ground, so it hurts what is there.
     */
    public record Secondary(float keel, boolean grounded) {}

    public enum Phase { FALLING, SKIDDING, DOWN }

    public final Warship ship;
    /** The crash machinery's key for this wreck, distinct from every aircraft id. */
    final long key;
    private Phase phase = Phase.FALLING;
    private float seconds;
    private float vx;
    private float vy;
    private final float ax;
    private final float ay;
    private final float spin;
    private final long seed;
    private final Weight weight;
    private float struckX = Float.NaN;
    private float struckY = Float.NaN;
    private float struckAt = -1f;
    private float burstX;
    private float burstY;
    /** The body factor the hull was drawn at when it burst. */
    private final float burstBody;
    /** True landed scale against this hull's flight size ({@link LandedScale#berthFactor()}), at most its flight size. */
    private final float groundBody;

    WarshipWreck(Warship ship, long key) {
        this.ship = ship;
        this.key = key;
        this.burstX = ship.x;
        this.burstY = ship.y;
        this.burstBody = ship.bodyScale();
        // A hull drawn below the standard presence (a gunship at the Onslaught's reduced body) keeps that body on the
        // ground: a wreck comes down to its landed size, never grows past the size it flew at.
        this.groundBody = Math.min(1f, LandedScale.berthFactor(ship.presence()));
        this.vx = ship.velocityX;
        this.vy = ship.velocityY;
        this.seed = WarshipAirRules.roundSeed(ship.hullId, ship.serial(), key, ship.faction.ordinal());
        this.weight = Weight.of(ship.shipClass().size());
        float speed = (float) Math.hypot(vx, vy);
        // A hull already moving keeps going that way; one that was hovering slides off to a side its hash chooses.
        double direction = speed > 0.5f ? Math.atan2(vy, vx)
                : Math.toRadians(ship.facingDegrees + 90f + (WarshipAirRules.unit(seed, 0) - 0.5f) * 140f);
        float push = Math.max(0f, SLIP_SPEED - speed) / FALL_SECONDS;
        this.ax = (float) Math.cos(direction) * push;
        this.ay = (float) Math.sin(direction) * push;
        this.spin = (WarshipAirRules.unit(seed, 1) * 2f - 1f) * MAX_SPIN;
    }

    public Phase phase() {
        return phase;
    }

    /** Seconds since the burst. */
    public float seconds() {
        return seconds;
    }

    /** How far up the hull still is, 1 at the burst to 0 at the strike; presentation only. */
    public float lift() {
        if (phase != Phase.FALLING) return 0f;
        float t = Math.min(1f, seconds / FALL_SECONDS);
        return 1f - t * t;
    }

    /**
     * The factor the wreck's body is drawn at against the hull's flight size: its size at the burst easing to true
     * landed scale by the strike, and true scale on the ground from then on.
     */
    public float bodyScale() {
        if (phase != Phase.FALLING) return groundBody;
        return LandedScale.fallFactor(burstBody, groundBody, seconds / FALL_SECONDS);
    }

    /** The radius the wreck crushes when it strikes: half the larger of its drawn length and beam on the ground. */
    public float crushRadius() {
        return 0.5f * Math.max(ship.lengthCells, ship.widthCells) * groundBody;
    }

    /** The radius it sweeps as it skids: half its drawn beam on the ground. */
    public float skidRadius() {
        return 0.5f * Math.min(ship.lengthCells, ship.widthCells) * groundBody;
    }

    /** How hard this wreck comes down, by its hull's size. */
    public Weight weight() {
        return weight;
    }

    /** The point {@code keel} of the way from bow (0) to stern (1) along the hull as it is drawn now, in cells. */
    public float[] keelPoint(float keel) {
        double r = Math.toRadians(ship.facingDegrees);
        float along = (0.5f - keel) * ship.lengthCells * bodyScale();
        return new float[]{ship.x + along * (float) Math.cos(r), ship.y + along * (float) Math.sin(r)};
    }

    /**
     * The secondary blasts due after {@code before} and up to {@code after} seconds from the burst: the fall's spread
     * evenly over it, then the ground's rippling down the keel one {@link #SECONDARY_GAP_SECONDS} apart from the
     * strike. Where along the keel each goes is a hash of the hull, so a wreck blows up the same way on every play.
     */
    public List<Secondary> secondariesBetween(float before, float after) {
        List<Secondary> due = new ArrayList<>();
        for (int i = 0; i < weight.fallSecondaries; i++) {
            float at = FALL_SECONDS * (i + 1) / (weight.fallSecondaries + 1);
            if (at > before && at <= after) due.add(new Secondary(keelFor(i), false));
        }
        for (int i = 0; i < weight.secondaries; i++) {
            float at = FALL_SECONDS + SECONDARY_GAP_SECONDS * (i + 1);
            if (at > before && at <= after) due.add(new Secondary(keelFor(weight.fallSecondaries + i), true));
        }
        return due;
    }

    /** Whether every secondary blast this wreck has is spent by {@code seconds}. */
    boolean secondariesSpent(float seconds) {
        return seconds >= FALL_SECONDS + SECONDARY_GAP_SECONDS * weight.secondaries;
    }

    private float keelFor(int i) {
        return 0.1f + 0.8f * WarshipAirRules.unit(seed, 2 + i);
    }

    /** Where the hull burst. */
    public float burstX() {
        return burstX;
    }

    public float burstY() {
        return burstY;
    }

    /** Where it struck the ground, or NaN while it is still falling. */
    public float struckX() {
        return struckX;
    }

    public float struckY() {
        return struckY;
    }

    /** Seconds after the burst at which it struck, or -1 while it is still falling. */
    public float struckAt() {
        return struckAt;
    }

    /** Advances the fall or the skid by {@code dt}; returns what changed this tick. */
    Step advance(float dt) {
        if (phase == Phase.DOWN) {
            // The clock keeps running while secondaries are still to go off on a hull already at rest.
            if (!secondariesSpent(seconds)) seconds += dt;
            return Step.NONE;
        }
        float before = seconds;
        seconds += dt;
        if (phase == Phase.FALLING) {
            float airborne = Math.min(dt, Math.max(0f, FALL_SECONDS - before));
            ship.x += vx * airborne + 0.5f * ax * airborne * airborne;
            ship.y += vy * airborne + 0.5f * ay * airborne * airborne;
            vx += ax * airborne;
            vy += ay * airborne;
            ship.facingDegrees = WarshipFacing.wrap(ship.facingDegrees + spin * airborne);
            ship.velocityX = vx;
            ship.velocityY = vy;
            if (seconds < FALL_SECONDS) return Step.FALLING;
            phase = Phase.SKIDDING;
            struckX = ship.x;
            struckY = ship.y;
            struckAt = FALL_SECONDS;
            return Step.STRUCK;
        }
        float speed = (float) Math.hypot(vx, vy);
        float next = Math.max(0f, speed - SKID_DECELERATION * dt);
        if (speed > 0f) {
            float moving = Math.min(dt, speed / SKID_DECELERATION);
            float distance = (speed + next) * 0.5f * moving;
            ship.x += vx / speed * distance;
            ship.y += vy / speed * distance;
            vx *= next / speed;
            vy *= next / speed;
        }
        ship.velocityX = vx;
        ship.velocityY = vy;
        if (next > 0f) return Step.SKIDDING;
        phase = Phase.DOWN;
        return Step.CAME_TO_REST;
    }

    /** What a tick of the fall did. */
    enum Step { NONE, FALLING, STRUCK, SKIDDING, CAME_TO_REST }
}
