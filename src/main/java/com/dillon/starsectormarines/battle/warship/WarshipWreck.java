package com.dillon.starsectormarines.battle.warship;

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
    private float struckX = Float.NaN;
    private float struckY = Float.NaN;
    private float struckAt = -1f;
    private float burstX;
    private float burstY;
    /** The body factor the hull was drawn at when it burst. */
    private final float burstBody;
    /** True landed scale against this hull's flight size ({@link LandedScale#berthFactor()}). */
    private final float groundBody;

    WarshipWreck(Warship ship, long key) {
        this.ship = ship;
        this.key = key;
        this.burstX = ship.x;
        this.burstY = ship.y;
        this.burstBody = ship.bodyScale();
        this.groundBody = LandedScale.berthFactor(ship.presence());
        this.vx = ship.velocityX;
        this.vy = ship.velocityY;
        long seed = WarshipAirRules.roundSeed(ship.hullId, ship.serial(), key, ship.faction.ordinal());
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
        if (phase == Phase.DOWN) return Step.NONE;
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
