package com.dillon.starsectormarines.battle.ground;

import com.dillon.starsectormarines.battle.turret.TurretKind;

/**
 * Static config for each ground-vehicle variant — sprite, capacity, handling
 * tunables, visual footprint. Parallels {@link com.dillon.starsectormarines.battle.air.ShuttleType}
 * for ground craft. The sim ticks each {@link Vehicle}'s {@link GroundBody}
 * directly — different variants can supply different kinematic models via
 * {@link #createBody()}, so a future {@code TANK} entry can return a
 * differential-drive {@code TrackedBody} instead of a {@link BicycleBody}
 * without changing {@link GroundSystem} at all.
 *
 * <p>Each constant overrides {@link #createBody()} with its own kinematic model
 * and constructor args. The sprite path resolves against the mod's atlas.
 */
public enum VehicleType {

    /**
     * Armored personnel carrier — four marines, roof-mounted heavy MG.
     * Slower and heavier than the militia truck; stays parked after deboard
     * and provides sustained fire support from the turret until the battle
     * ends or the vehicle is destroyed.
     */
    HEAVY_APC(
            "graphics/battle/vehicles/army-apc.png", /*spriteFrame*/ 0, /*frameCount*/ 2,
            /*spriteFacingOffsetDeg*/ -90f,
            /*capacity*/ 4, /*visualLengthCells*/ 2.4f, /*visualWidthCells*/ 1.4f,
            /*maxSpeed*/ 2.8f, /*accel*/ 1.8f, /*brakingAccel*/ 3.5f,
            /*deboardInterval*/ 0.8f, /*lookAheadCells*/ 2.2f,
            /*turretFrame*/ 1, /*turretMountX*/ -0.15866698f, /*turretMountY*/ 0.26800027f,
            /*turretPivotX*/ 0.108333334f, /*turretPivotY*/ 0.024999995f, /*turretVisualCells*/ 0.7f, /*turretSpriteFacingOffsetDeg*/ -90f,
            /*turretKind*/ TurretKind.HEAVY_MG, /*departsAfterDeboard*/ false, /*overwatchDurationSec*/ 20f) {
        @Override
        public GroundBody createBody() {
            return new BicycleBody(
                    /*wheelbaseCells*/ 1.6f,
                    /*maxSteeringDeg*/ 22f, /*steeringSlewDegPerSec*/ 150f,
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

    /** Sheet frame index for the turret sprite, or {@code -1} if this variant has no turret. */
    public final int turretFrame;
    /** Turret mount point on the chassis, cells from chassis center. +X right, +Y forward in chassis local frame. */
    public final float turretMountX;
    public final float turretMountY;
    /** Rotation center within the turret sprite, cells from turret sprite center. +X right, +Y forward in turret local frame. */
    public final float turretPivotX;
    public final float turretPivotY;
    /** Visual size of the turret sprite (longest axis), cells. Same semantics as {@link #visualLengthCells}. */
    public final float turretVisualCells;
    /** Facing offset for the turret sprite, degrees. Separate from {@link #spriteFacingOffsetDeg} because chassis and turret frames may face different directions in the sheet. */
    public final float turretSpriteFacingOffsetDeg;
    /** Weapon kind for the vehicle-mounted turret, or {@code null} if no functional weapon. Drives the aim/fire loop in {@link GroundSystem}. */
    public final TurretKind turretKind;
    /** If true, the vehicle departs immediately after all marines deboard (truck behavior). If false, it enters OVERWATCH first. */
    public final boolean departsAfterDeboard;
    /** Sim-seconds the vehicle holds overwatch before departing. Only meaningful when {@link #departsAfterDeboard} is false. */
    public final float overwatchDurationSec;

    public boolean hasTurretWeapon() { return turretKind != null && turretFrame >= 0; }

    VehicleType(String spritePath, int spriteFrame, int frameCount,
                float spriteFacingOffsetDeg,
                int capacity, float visualLengthCells, float visualWidthCells,
                float maxSpeed, float accel, float brakingAccel,
                float deboardInterval, float lookAheadCells,
                int turretFrame, float turretMountX, float turretMountY,
                float turretPivotX, float turretPivotY, float turretVisualCells,
                float turretSpriteFacingOffsetDeg,
                TurretKind turretKind, boolean departsAfterDeboard,
                float overwatchDurationSec) {
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
        this.turretFrame = turretFrame;
        this.turretMountX = turretMountX;
        this.turretMountY = turretMountY;
        this.turretPivotX = turretPivotX;
        this.turretPivotY = turretPivotY;
        this.turretVisualCells = turretVisualCells;
        this.turretSpriteFacingOffsetDeg = turretSpriteFacingOffsetDeg;
        this.turretKind = turretKind;
        this.departsAfterDeboard = departsAfterDeboard;
        this.overwatchDurationSec = overwatchDurationSec;
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
