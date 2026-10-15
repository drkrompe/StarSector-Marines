package com.dillon.starsectormarines.battle.air;

/**
 * Where a cover fighter flies to get its guns onto a hostile aircraft: the geometry of a tail chase.
 *
 * <p>A nose cannon fires on a narrow cone ({@link AirToAirGun#CONE_DEGREES}) out to {@link AirToAirGun#RANGE_CELLS}, so
 * how long a pass keeps the prey in that cone is the whole of the fighter's firepower. Met head-on the two craft close at
 * the <em>sum</em> of their speeds and the prey is inside the window for about half a second; a fighter that sits
 * <b>behind</b> its prey closes at the <em>difference</em> and can hold the window for as long as it can hold the station.
 * So against a prey it is clearly faster than, the fighter does not fly at the prey; it flies at a <b>slot in the prey's
 * wake</b> ({@link #TRAIL_CELLS} behind it, led along its track), and it can do that only from the rear quarter. From
 * anywhere else it flies lead pursuit, which both takes whatever window the pass gives and, once it has gone by, turns
 * it in behind.
 *
 * <ul>
 *   <li><b>{@link Stage#PURSUE}</b>: in the rear quarter (within {@link #REAR_HALF_ANGLE_DEGREES} of the prey's tail
 *       axis) of a prey it outruns by {@link #MIN_OVERTAKE_CELLS_PER_SECOND} or more. Lead pursuit of the slot; inside a
 *       couple of trail lengths the fighter brakes onto the slot so it holds station instead of overrunning the prey.</li>
 *   <li><b>{@link Stage#CLOSE}</b>: a prey it outruns, from ahead of or beside it. Lead pursuit of the prey itself.</li>
 *   <li><b>{@link Stage#MATCHED}</b>: a prey it cannot outrun. A stern chase never closes on an equal, and the equal
 *       turns, so the fighter flies plain lead pursuit and takes the window the geometry gives.</li>
 *   <li><b>{@link Stage#TRACKLESS}</b>: a prey that is hovering or nearly so has no track to be behind; plain lead
 *       pursuit.</li>
 * </ul>
 *
 * <p>This is geometry and nothing else. Whether the fighter should be chasing at all (the magazine, the visit time,
 * anti-air it knows of over the prey) is the pool's and the flight system's; whether a round hits is
 * {@link AirToAirGun#hitChance}. Pure and stateless: the fighter re-asks every tick, so a prey that turns is followed
 * rather than predicted.
 *
 * <p>UNTUNED: the trail and the rear quarter are first-pass figures.
 */
public final class AirIntercept {

    /** How far behind the prey the fighter holds, in cells: inside the guns' point-blank share of their range. UNTUNED. */
    public static final float TRAIL_CELLS = 8f;
    /** A fighter within this many degrees of the prey's tail axis is in its rear quarter. UNTUNED. */
    public static final float REAR_HALF_ANGLE_DEGREES = 60f;
    /** A prey slower than this has no track to get behind, in cells per second. */
    public static final float MIN_TRACK_SPEED = 3f;
    /** The speed a fighter must have over its prey, in cells per second, before a stern chase is worth flying. UNTUNED. */
    public static final float MIN_OVERTAKE_CELLS_PER_SECOND = 6f;
    /** Longest the fighter leads the prey, in seconds. */
    public static final float MAX_LEAD_SECONDS = 2.5f;
    /** Inside this many trail lengths of the slot the fighter brakes onto it rather than flying through it. Matching the prey's speed more finely did worse in the seed sweeps: the fighter fell out of the cone in the prey's turns. */
    private static final float BRAKE_TRAILS = 2.5f;

    /** What the fighter is doing about its geometry this tick. */
    public enum Stage { PURSUE, CLOSE, MATCHED, TRACKLESS }

    /** The point to steer at, how, and which stage chose it. */
    public record Plan(float x, float y, SteeringMode mode, Stage stage) {}

    private AirIntercept() {}

    /**
     * What to steer at this tick.
     *
     * @param topSpeed the fighter's top speed, cells per second
     */
    public static Plan plan(float shooterX, float shooterY, float shooterVx, float shooterVy, float topSpeed,
                            float preyX, float preyY, float preyVx, float preyVy) {
        float preySpeed = (float) Math.hypot(preyVx, preyVy);
        float range = (float) Math.hypot(preyX - shooterX, preyY - shooterY);
        float speed = Math.max(1f, Math.max(topSpeed, (float) Math.hypot(shooterVx, shooterVy)));
        float[] lead = AirToAirGun.leadPoint(shooterX, shooterY, speed, preyX, preyY, preyVx, preyVy);
        if (preySpeed < MIN_TRACK_SPEED) return new Plan(lead[0], lead[1], SteeringMode.CRUISE, Stage.TRACKLESS);
        if (topSpeed - preySpeed < MIN_OVERTAKE_CELLS_PER_SECOND) {
            return new Plan(lead[0], lead[1], SteeringMode.CRUISE, Stage.MATCHED);
        }
        float dx = preyVx / preySpeed;
        float dy = preyVy / preySpeed;
        float along = (shooterX - preyX) * dx + (shooterY - preyY) * dy;
        if (!isRearQuarter(along, range)) return new Plan(lead[0], lead[1], SteeringMode.CRUISE, Stage.CLOSE);
        float leadSeconds = Math.min(MAX_LEAD_SECONDS, range / speed);
        float slotX = preyX + preyVx * leadSeconds - dx * TRAIL_CELLS;
        float slotY = preyY + preyVy * leadSeconds - dy * TRAIL_CELLS;
        float toSlot = (float) Math.hypot(slotX - shooterX, slotY - shooterY);
        SteeringMode mode = toSlot <= TRAIL_CELLS * BRAKE_TRAILS ? SteeringMode.BRAKE_TO_STATION : SteeringMode.CRUISE;
        return new Plan(slotX, slotY, mode, Stage.PURSUE);
    }

    /** Whether a fighter {@code along} the prey's track (negative: behind it) and {@code range} from it is in the rear quarter. */
    public static boolean isRearQuarter(float along, float range) {
        return along < 0f && range > 1e-4f
                && -along >= range * (float) Math.cos(Math.toRadians(REAR_HALF_ANGLE_DEGREES));
    }

    /** Whether a fighter at the first position is in the rear quarter of a prey at the second moving with the given velocity. */
    public static boolean isRearQuarter(float shooterX, float shooterY, float preyX, float preyY, float preyVx, float preyVy) {
        float speed = (float) Math.hypot(preyVx, preyVy);
        if (speed < MIN_TRACK_SPEED) return false;
        float relX = shooterX - preyX;
        float relY = shooterY - preyY;
        float along = (relX * preyVx + relY * preyVy) / speed;
        return isRearQuarter(along, (float) Math.hypot(relX, relY));
    }
}
