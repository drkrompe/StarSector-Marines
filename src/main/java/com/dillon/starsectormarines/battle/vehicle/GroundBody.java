package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.Crippled;
/**
 * Pose + integration step for one ground vehicle. Sibling abstraction to
 * {@link com.dillon.starsectormarines.battle.air.AirBody} — both expose
 * {@link #x}/{@link #y}/{@link #facingDegrees} for the renderer, but each
 * subclass models its own kinematics: {@link BicycleBody} for car/truck-style
 * front-steer-rear-follows behavior, future siblings for tanks (differential
 * drive with in-place pivot), holonomic mechs (omnidirectional translate),
 * etc.
 *
 * <p>The body is driven by a single per-tick call from
 * {@link GroundSystem}: "advance toward this carrot point at no more than
 * this target speed for {@code dt} seconds." The carrot picker (pure pursuit)
 * lives in {@link PurePursuit} so every {@link GroundBody} subclass shares
 * path following — subclasses only differ in how they convert
 * ({@code carrotX, carrotY, targetSpeed}) into pose updates.
 *
 * <p>Facing convention: 0° points sprite-north (+Y), positive rotates
 * counterclockwise — same as {@link com.dillon.starsectormarines.battle.air.AirBody}
 * so renderer code is interchangeable.
 */
public abstract class GroundBody {

    /** Position, cells. Renderer reads this directly. */
    public float x, y;

    /** Facing — 0° = +Y, positive CCW. Matches Starsector sprite convention. */
    public float facingDegrees;

    /**
     * Current forward speed along {@link #facingDegrees}, cells/sec. Side
     * velocity is identically zero for the bicycle model; subclasses with
     * non-zero lateral velocity should still surface their translational
     * magnitude here so debug overlays / engine-sound code can read it.
     */
    public float speed;

    /**
     * Whether the drive is gone ({@link HullLocations}): for the rest of the battle the hull drives, reverses and
     * turns at {@link Crippled#SPEED_SHARE} of its speed. It is slowed, never stranded.
     */
    public boolean crippled;

    /**
     * What the engine fitted to the hull makes of its speed ({@link VehicleEngine}): a multiple of the hull's own top
     * speed and of its acceleration and braking, 1 for the hull's own drive. Set once, at the drop.
     */
    public float speedFactor = 1f;

    /**
     * Velocity and spin a hit left the hull with, cells a second and radians a second, on top of its own
     * drive; {@link HullSlide} spends them against the tires or tracks. Zero when nothing has struck it.
     */
    public float knockVx, knockVy, knockOmega;
    /** The hull rocking on its suspension after a hit: offset from the chassis, cells, and its rate. */
    public float joltX, joltY, joltVx, joltVy;

    /**
     * Bumps: how many times the hull has run into something that stopped it ({@link #recordBlocked}), and how fast it
     * was going the last time. Presentation reads them for the impact sound; nothing in the simulation does.
     */
    public int bumps;
    public float lastBumpSpeed;

    /** Below this speed, cells a second, a stopped move is the hull leaning on what holds it, not running into it. */
    public static final float BUMP_MIN_SPEED = .5f;

    /**
     * A move was stopped by terrain at {@code impactSpeed}, the speed the hull had before it was stopped. Counts as a
     * bump only at {@link #BUMP_MIN_SPEED} or more, so a hull held against a wall, which creeps up from a standstill
     * each tick, bumps once as it hits and not again while it pushes.
     */
    public void recordBlocked(float impactSpeed) {
        float speed = Math.abs(impactSpeed);
        if (!(speed >= BUMP_MIN_SPEED)) return;
        bumps++;
        lastBumpSpeed = speed;
    }

    /** Accepted signed chassis travel for distance-driven tread UVs, never a render clock. */
    public double visualTravelCells, visualYawRadians;

    public void recordVisualTravel(float previousX, float previousY, float previousFacing) {
        float yaw = ((facingDegrees - previousFacing + 540f) % 360f) - 180f;
        double midpoint = Math.toRadians(previousFacing + yaw * .5f);
        visualTravelCells += -(x - previousX) * Math.sin(midpoint)
                + (y - previousY) * Math.cos(midpoint);
        visualYawRadians += Math.toRadians(yaw);
    }

    /**
     * {@code degrees} folded into (-360, 360), the range a hull's heading is kept in, and unchanged when already
     * there. The {@code (bearing - facing + 540) % 360 - 180} wrap every reader of a heading uses is right only in that
     * range: with a heading of 607 degrees it read a carrot dead ahead as a full turn off, and the Burn's allied hulls
     * reversed away from their posts for the rest of the battle.
     */
    public static float foldFacing(float degrees) {
        return degrees > 360f || degrees < -360f ? degrees % 360f : degrees;
    }

    /** Snap pose with zero velocity. Use at spawn / respawn / teleport boundaries. */
    public void teleport(float px, float py, float facingDeg) {
        x = px;
        y = py;
        facingDegrees = foldFacing(facingDeg);
        speed = 0f;
        knockVx = knockVy = knockOmega = 0f;
        joltX = joltY = joltVx = joltVy = 0f;
        visualTravelCells = visualYawRadians = 0;
        onTeleport();
    }

    /** How fast the hull's heading is turning, degrees a second, positive counterclockwise. Zero for a body that cannot turn. */
    public float yawRateDegPerSec() {
        return 0f;
    }

    /** Hook for subclasses to reset model-specific state (e.g., steering angle) on teleport. */
    protected void onTeleport() {}

    /** Distance to ({@code gx, gy}), cells. */
    public float distanceTo(float gx, float gy) {
        float dx = gx - x, dy = gy - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Advance pose one tick toward ({@code carrotX, carrotY}) at no more than
     * {@code targetSpeed} cells/sec. The carrot is a moving look-ahead point
     * along the path, not the next waypoint — pure pursuit keeps it ahead of
     * the body so the vehicle never tries to spin around a stationary target.
     * The {@code targetSpeed} cap is set by the caller from remaining path
     * length so the body brakes to a clean stop at the LZ.
     */
    public abstract void tick(float carrotX, float carrotY, float targetSpeed, float dt);
}
