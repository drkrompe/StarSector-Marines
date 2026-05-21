package com.dillon.starsectormarines.battle.ground;

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

    /** Snap pose with zero velocity. Use at spawn / respawn / teleport boundaries. */
    public void teleport(float px, float py, float facingDeg) {
        x = px;
        y = py;
        facingDegrees = facingDeg;
        speed = 0f;
        onTeleport();
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
