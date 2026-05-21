package com.dillon.starsectormarines.battle.ground;

/**
 * Static config for each ground-vehicle variant — sprite, capacity, handling
 * tunables, visual footprint. Parallels {@link com.dillon.starsectormarines.battle.air.ShuttleType}
 * for ground craft. The sim ticks each {@link Vehicle}'s {@link GroundBody}
 * directly — different variants can supply different kinematic models via
 * {@link #createBody()}, so a future {@code TANK} entry can return a
 * differential-drive {@code TrackedBody} instead of a {@link BicycleBody}
 * without changing {@link GroundSystem} at all.
 *
 * <p>V1 ships a single variant ({@link #MILITIA_TRUCK}); future entries
 * (heavy APC, armored car, tank) slot in alongside as new enum constants.
 * Each constant overrides {@link #createBody()} with its own kinematic model
 * and constructor args. The sprite path resolves against the mod's atlas.
 */
public enum VehicleType {

    /**
     * Default defender-reinforcement transport — six militia, ~2 cell visual
     * footprint. Steers via {@link BicycleBody}: ~3-cell minimum turn radius
     * (wheelbase 1.4 / tan 25°) for "trucks have to commit to corners" feel,
     * driver wheel-slew at 180°/s keeps cornering smooth, and pure-pursuit
     * carrot at 2 cells out keeps it from orbiting close waypoints.
     */
    MILITIA_TRUCK(
            "graphics/battle/trucks_2.png", /*spriteFrame*/ 1, /*frameCount*/ 2,
            /*spriteFacingOffsetDeg*/ 90f,
            /*capacity*/ 6, /*visualLengthCells*/ 2.0f, /*visualWidthCells*/ 1.1f,
            /*maxSpeed*/ 3.5f, /*accel*/ 2.5f, /*brakingAccel*/ 4f,
            /*deboardInterval*/ 0.6f, /*lookAheadCells*/ 2.0f) {
        @Override
        public GroundBody createBody() {
            return new BicycleBody(
                    /*wheelbaseCells*/ 1.4f,
                    /*maxSteeringDeg*/ 25f, /*steeringSlewDegPerSec*/ 180f,
                    accel, brakingAccel, maxSpeed);
        }
    };

    public final String spritePath;
    /** Which frame in the horizontal sprite-sheet to draw. 0 = leftmost, increments rightward. */
    public final int spriteFrame;
    /** Total frames in the sprite-sheet. The renderer divides the texture width by this to get each frame's UV rect. */
    public final int frameCount;
    /**
     * Degrees to add to {@link Vehicle#body}.{@code facingDegrees} when
     * rendering, to align the texture's natural "forward" with the sim's
     * "facing=0 is +Y" convention. Vanilla Starsector ship sprites point
     * north (offset 0); trucks_2.png frames point east (cab on the right
     * edge of each frame) so they need +90° to rotate the cab toward the
     * sim's north when the body faces north.
     */
    public final float spriteFacingOffsetDeg;
    public final int capacity;
    /** Sprite long-axis (nose-to-tail) in cells at facing 0. */
    public final float visualLengthCells;
    /** Sprite short-axis (side-to-side) in cells at facing 0. */
    public final float visualWidthCells;
    /** Cruise / max forward velocity, cells/sec. The path follower clamps target speed to this. */
    public final float maxSpeed;
    /** Forward thrust cap, cells/sec². Applied when speed is below the path-follower's target. */
    public final float accel;
    /** Forward braking cap, cells/sec². Sizes the brake taper into the final waypoint. */
    public final float brakingAccel;
    /** Sim-seconds between marine drop-offs after the truck reaches its LZ. */
    public final float deboardInterval;
    /**
     * Pure-pursuit look-ahead distance, cells — how far ahead along the
     * polyline the carrot sits. Larger = smoother corners but wider corner
     * cuts; smaller = tighter tracking but jitter on tight turns. ~1 vehicle
     * length is a reasonable starting point.
     */
    public final float lookAheadCells;

    VehicleType(String spritePath, int spriteFrame, int frameCount,
                float spriteFacingOffsetDeg,
                int capacity, float visualLengthCells, float visualWidthCells,
                float maxSpeed, float accel, float brakingAccel,
                float deboardInterval, float lookAheadCells) {
        this.spritePath = spritePath;
        this.spriteFrame = spriteFrame;
        this.frameCount = frameCount;
        this.spriteFacingOffsetDeg = spriteFacingOffsetDeg;
        this.capacity = capacity;
        this.visualLengthCells = visualLengthCells;
        this.visualWidthCells = visualWidthCells;
        this.maxSpeed = maxSpeed;
        this.accel = accel;
        this.brakingAccel = brakingAccel;
        this.deboardInterval = deboardInterval;
        this.lookAheadCells = lookAheadCells;
    }

    /**
     * Factory for this variant's kinematic body. Each enum constant returns
     * the model appropriate for its handling — trucks/cars get
     * {@link BicycleBody}, tanks would get a future {@code TrackedBody},
     * mechs a holonomic body, etc. This is the only place per-variant
     * kinematic params live; {@link GroundSystem} sees only the abstract
     * {@link GroundBody}.
     */
    public abstract GroundBody createBody();
}
