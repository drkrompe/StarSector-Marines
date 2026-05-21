package com.dillon.starsectormarines.battle.ground;

import com.dillon.starsectormarines.battle.air.AirHandling;

/**
 * Static config for each ground-vehicle variant — sprite, capacity, handling
 * profile, visual footprint. Parallels {@link com.dillon.starsectormarines.battle.air.ShuttleType}
 * for ground craft: the sim ticks a {@link Vehicle}'s {@link com.dillon.starsectormarines.battle.air.AirBody}
 * under this type's {@link AirHandling} numbers, exactly the same code path
 * that drives shuttles. The "ground" in the name is gameplay-facing — these
 * are trucks/APCs that path through the road network rather than fly over
 * it; mechanically the kinematics are still the air-body steering loop,
 * just tuned with high lateral damping and low turn rate so the truck reads
 * as on rails instead of drifting.
 *
 * <p>V1 ships a single variant ({@link #MILITIA_TRUCK}); future entries
 * (heavy APC, armored car) slot in alongside it as new enum constants. The
 * sprite path resolves against the mod's own atlas (not the vanilla game),
 * so trucks ship inside the mod.
 */
public enum VehicleType implements AirHandling {

    /**
     * Default defender-reinforcement transport — six militia, ~2 cell visual
     * footprint, slow turn / heavy lateral damping for an on-rails truck
     * feel. Uses the army-truck frame (index 1) of the shared trucks sheet.
     */
    MILITIA_TRUCK(
            "graphics/battle/trucks_2.png", /*spriteFrame*/ 1, /*frameCount*/ 2,
            /*spriteFacingOffsetDeg*/ 90f,
            /*capacity*/ 6, /*visualLengthCells*/ 2.0f, /*visualWidthCells*/ 1.1f,
            /*maxSpeed*/ 3.5f, /*deboardInterval*/ 0.6f,
            Profiles.TRUCK);

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
    /** Cruise / max forward velocity, cells/sec. Used as the AirHandling#maxSpeed cap. */
    public final float maxSpeed;
    /** Sim-seconds between marine drop-offs after the truck reaches its LZ. */
    public final float deboardInterval;
    public final HandlingProfile handling;

    VehicleType(String spritePath, int spriteFrame, int frameCount,
                float spriteFacingOffsetDeg,
                int capacity, float visualLengthCells, float visualWidthCells,
                float maxSpeed, float deboardInterval,
                HandlingProfile handling) {
        this.spritePath = spritePath;
        this.spriteFrame = spriteFrame;
        this.frameCount = frameCount;
        this.spriteFacingOffsetDeg = spriteFacingOffsetDeg;
        this.capacity = capacity;
        this.visualLengthCells = visualLengthCells;
        this.visualWidthCells = visualWidthCells;
        this.maxSpeed = maxSpeed;
        this.deboardInterval = deboardInterval;
        this.handling = handling;
    }

    @Override public float maxSpeed()                 { return maxSpeed; }
    @Override public float accel()                    { return handling.accel; }
    @Override public float brakingAccel()             { return handling.brakingAccel; }
    @Override public float maxTurnRateDegPerSec()     { return handling.maxTurnRateDegPerSec; }
    @Override public float lateralDriftDamping()      { return handling.lateralDriftDamping; }
    @Override public float stationDamping()           { return handling.stationDamping; }

    /** Bundle of per-profile handling tunables. Same shape as {@code ShuttleType.HandlingProfile} for symmetry; kept separate so ground / air tunables don't share constants by accident. */
    public static final class HandlingProfile {
        public final float accel;
        public final float brakingAccel;
        public final float maxTurnRateDegPerSec;
        public final float lateralDriftDamping;
        public final float stationDamping;

        public HandlingProfile(float accel, float brakingAccel, float maxTurnRateDegPerSec,
                               float lateralDriftDamping, float stationDamping) {
            this.accel = accel;
            this.brakingAccel = brakingAccel;
            this.maxTurnRateDegPerSec = maxTurnRateDegPerSec;
            this.lateralDriftDamping = lateralDriftDamping;
            this.stationDamping = stationDamping;
        }
    }

    /**
     * Tier presets for ground vehicles. Differs from the air tiers chiefly
     * in lateral damping — wheels don't slide, so damping is much higher
     * than even the BUS air profile. Turn rate is also low to read as a
     * heavy ground vehicle that has to commit to corners.
     */
    public static final class Profiles {
        /**
         * Generic heavy cargo truck — slow accel (1.4s to cruise speed) and
         * a modest brakingAccel so stops take a couple cells. Pairs with
         * {@link com.dillon.starsectormarines.battle.ground.GroundSystem}'s
         * waypoint look-ahead: on long straights the look-ahead target is
         * far away so brake-to-station hits max speed, and into corners the
         * target collapses to the corner cell so the truck naturally decels
         * before turning.
         */
        public static final HandlingProfile TRUCK = new HandlingProfile(
                /*accel*/ 2.5f, /*brakingAccel*/ 4f, /*maxTurnRateDegPerSec*/ 55f,
                /*lateralDriftDamping*/ 20f, /*stationDamping*/ 25f);

        private Profiles() {}
    }
}
