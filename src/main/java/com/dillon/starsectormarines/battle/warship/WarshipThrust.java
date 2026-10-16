package com.dillon.starsectormarines.battle.warship;

/**
 * How hard a hovering warship's engines are working, read off the motion the
 * simulation actually gave it.
 *
 * <p>A capital does not fly a thrust model of its own: it hovers toward a
 * station at a mass-limited speed, accelerating, cruising and braking onto it.
 * The engines are presentation, so this reads that motion back rather than
 * asking the simulation for a throttle it does not have: a stopped ship idles at
 * a low glow, one that is picking up speed or coming about works its nozzles
 * harder, one holding speed barely glows above its hover, and one braking onto
 * its station dims.
 *
 * <p>The level is a continuous function of speed and of the gap between the
 * speed the ship is flying and the speed its next tick wants, so a flame swells
 * and fades rather than snapping between states.
 *
 * <p>Lives beside {@link Warship} because it reads the ship's motion and station,
 * which the rest of the code has no business touching.
 */
public final class WarshipThrust {

    /**
     * A hovering ship's glow: the nozzles are holding the hull up and nothing more is being asked of them. Low,
     * because a ship of the line over a battle spends most of its time here, and a torch at rest reads as a ship
     * burning to stand still.
     */
    public static final float IDLE_LEVEL = 0.15f;
    /** The level at top speed with nothing left to gain: a cruising ship barely glows above its hover. */
    public static final float CRUISE_LEVEL = 0.25f;
    /** The level at the hardest braking: retro thrust is not the aft nozzles' work. */
    public static final float BRAKE_LEVEL = 0.08f;
    /** What an aft nozzle keeps when the ship is pushed sideways to its axis. */
    public static final float OFF_AXIS_SHARE = 0.5f;
    /**
     * The master level of a hull coming about at its full turn rate. A heavy ship wearing round is working its
     * engines, so it flares; a modest flare, below a ship gathering way from a standstill.
     */
    public static final float TURN_LEVEL = 0.6f;
    /** How much more the nozzles on the outside of a turn burn than the ones on its inside, at full turn rate. */
    public static final float TURN_DIFFERENTIAL = 0.6f;
    /** Seconds of the ship's own acceleration the speed gap is judged against. */
    private static final float GAP_SECONDS = 0.5f;
    /** Below this thrust fraction braking is mild enough that every nozzle dims alike. */
    private static final float DIRECTIONAL_MIN = -0.2f;
    private static final float MOVING_SPEED_FRACTION = 0.02f;

    /**
     * The engines' state for one frame: the master {@code level} in {@code [0, 1]},
     * and the world direction the ship is being pushed, if any. {@code pushX/pushY}
     * are zero when there is no preferred direction, and every nozzle then burns alike.
     */
    public record Demand(float level, float pushX, float pushY, float turn) {

        /** A demand with no turn in it. */
        public Demand(float level, float pushX, float pushY) {
            this(level, pushX, pushY, 0f);
        }
    }

    private WarshipThrust() {}

    /**
     * How hard the ship is speeding up or slowing down, in {@code [-1, 1]}: the gap
     * between the speed it wants and the speed it has, as a share of half a second
     * of its own acceleration. {@code brakingOnCurve} says the ship is following the
     * speed profile that stops it on its station, where it tracks the wanted speed
     * closely and the gap alone would read as cruising.
     */
    public static float thrustFraction(float speed, float desiredSpeed, float acceleration,
                                       boolean brakingOnCurve) {
        if (brakingOnCurve) return -1f;
        float room = Math.max(1e-4f, acceleration * GAP_SECONDS);
        return Math.max(-1f, Math.min(1f, (desiredSpeed - speed) / room));
    }

    /**
     * The master level for a ship making {@code speedFraction} of its top speed
     * under {@code thrust}: idle at a standstill, brighter with speed, full when
     * accelerating and nearly out when braking.
     */
    public static float level(float speedFraction, float thrust) {
        float s = Math.max(0f, Math.min(1f, speedFraction));
        float t = Math.max(-1f, Math.min(1f, thrust));
        float base = IDLE_LEVEL + (CRUISE_LEVEL - IDLE_LEVEL) * s;
        return t >= 0f ? base + (1f - base) * t : base + (BRAKE_LEVEL - base) * -t;
    }

    /**
     * The share of the master level one nozzle burns. A nozzle pushes the ship
     * opposite to the way its flame points, so it works hardest when that is the
     * way the ship is being pushed: aft nozzles on a ship being pushed ahead, the
     * side nozzles on one being pushed across. {@code exhaustHeadingDegrees} is the
     * flame's world heading (hull facing plus the slot's angle), in the hull-facing
     * convention where 0 points along {@code +y} and positive turns counter-clockwise.
     * With no push direction every nozzle burns alike.
     */
    public static float slotShare(float pushX, float pushY, float exhaustHeadingDegrees) {
        float len = (float) Math.sqrt(pushX * pushX + pushY * pushY);
        if (len < 1e-5f) return 1f;
        double r = Math.toRadians(exhaustHeadingDegrees);
        // The flame's unit direction in the world is (-sin, cos); the push is its opposite.
        float alignment = (pushX * (float) Math.sin(r) - pushY * (float) Math.cos(r)) / len;
        return OFF_AXIS_SHARE + (1f - OFF_AXIS_SHARE) * Math.max(0f, alignment);
    }

    /**
     * The share of the master level a nozzle burns for the hull's turn: a nozzle pushes the ship along its flame's
     * opposite, so one on the side the turn swings away from works harder and one on the other side eases off.
     * {@code acrossShare} is the nozzle's offset across the hull as a share of half its beam, positive to
     * {@code +x} in the hull frame (starboard of a nose along {@code +y}); a counter-clockwise turn is driven by the
     * {@code +x} nozzles. With no turn every nozzle burns alike.
     */
    public static float turnShare(float turn, float acrossShare) {
        float across = Math.max(-1f, Math.min(1f, acrossShare));
        float t = Math.max(-1f, Math.min(1f, turn));
        return Math.max(0f, 1f + TURN_DIFFERENTIAL * t * across);
    }

    /** Reads the engines' state off {@code ship}'s motion this tick. */
    public static Demand of(Warship ship) {
        float vx = ship.velocityX;
        float vy = ship.velocityY;
        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        float top = Math.max(1e-4f, ship.maxSpeed);
        float desired = 0f;
        float toStationX = 0f;
        float toStationY = 0f;
        boolean brakingOnCurve = false;
        if (ship.hasStation()) {
            float dx = ship.stationX() - ship.x;
            float dy = ship.stationY() - ship.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            // The speed profile WarshipSystem flies: top speed through a waypoint,
            // otherwise the speed that still stops on the station.
            float stopSpeed = (float) Math.sqrt(2f * ship.deceleration * dist);
            desired = ship.isPassingThrough() ? ship.maxSpeed : Math.min(ship.maxSpeed, stopSpeed);
            if (dist > 0f) {
                toStationX = dx / dist;
                toStationY = dy / dist;
            }
            brakingOnCurve = !ship.isPassingThrough() && stopSpeed < ship.maxSpeed
                    && speed > MOVING_SPEED_FRACTION * 5f * top && speed >= 0.9f * desired;
        }
        float thrust = thrustFraction(speed, desired, ship.acceleration, brakingOnCurve);
        float turn = Math.max(-1f, Math.min(1f,
                ship.turnVelocity / Math.max(1e-4f, ship.maxTurnDegPerSec)));
        // Coming about works the engines whatever the translation is doing.
        float level = Math.max(level(speed / top, thrust), TURN_LEVEL * Math.abs(turn));
        boolean nearlyStopped = speed < MOVING_SPEED_FRACTION * top && thrust <= 0f;
        if (thrust < DIRECTIONAL_MIN || nearlyStopped) {
            return new Demand(level, 0f, 0f, turn);
        }
        if (thrust > 0f) return new Demand(level, toStationX, toStationY, turn);
        return new Demand(level, vx / Math.max(speed, 1e-4f), vy / Math.max(speed, 1e-4f), turn);
    }
}
