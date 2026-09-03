package com.dillon.starsectormarines.ops;

/**
 * Mission-authored shape and timing variance for paired Conquest arrivals.
 * Drop zones are physical beachheads; each player pair assigned to one of
 * those zones cycles until its share of the selected company has landed.
 *
 * <p><b>The shape is derived from the landing share unless a mission states
 * it.</b> {@link #dropZoneCount} and {@link #shuttlePairsPerZone} were fixed at
 * three and one, which is a lift that does not scale with the force: Full
 * Strength put 34 squads through the same three pairs Reinforced used for 17 and
 * landed its last squad with 449 ticks of the battle left. {@link #DERIVED}
 * leaves both to {@code OrbitalLift}, which sizes them from the committed seats
 * and the constant descent round trip so the force is down inside
 * {@link #landingShare} whatever its size. A stated value still wins outright —
 * a mission that wants one beachhead gets one.
 */
public record ConquestArrivalConfig(
        int dropZoneCount,
        int shuttlePairsPerZone,
        float timingJitterSec,
        LandingShare landingShare) {

    /**
     * The value that means "the mission is not saying" for either count.
     *
     * <p>Zero rather than a null Integer because these travel through fixture
     * JSON and a debug stepper, both of which are happier with a number that
     * steps down past one than with a boxed absence.
     */
    public static final int DERIVED = 0;

    public static final ConquestArrivalConfig DEFAULT =
            new ConquestArrivalConfig(DERIVED, DERIVED, 1f, LandingShare.DEFAULT);
    public static final ConquestArrivalConfig LEGACY =
            new ConquestArrivalConfig(1, 1, 0f, LandingShare.DEFAULT);

    public ConquestArrivalConfig {
        if (dropZoneCount < DERIVED) {
            throw new IllegalArgumentException("dropZoneCount must not be negative");
        }
        if (shuttlePairsPerZone < DERIVED) {
            throw new IllegalArgumentException("shuttlePairsPerZone must not be negative");
        }
        if (!Float.isFinite(timingJitterSec) || timingJitterSec < 0f) {
            throw new IllegalArgumentException(
                    "timingJitterSec must be finite and non-negative");
        }
        landingShare = landingShare != null ? landingShare : LandingShare.DEFAULT;
    }

    public ConquestArrivalConfig(int dropZoneCount, int shuttlePairsPerZone,
                                 float timingJitterSec) {
        this(dropZoneCount, shuttlePairsPerZone, timingJitterSec,
                LandingShare.DEFAULT);
    }

    public static ConquestArrivalConfig defaultFor(MissionType type) {
        return type == MissionType.CONQUEST ? DEFAULT : LEGACY;
    }

    /** True while either count is still the lift's to decide. */
    public boolean derivesItsShape() {
        return dropZoneCount == DERIVED || shuttlePairsPerZone == DERIVED;
    }

    /**
     * The pairs in the air, once both counts are concrete. Meaningless while
     * {@link #derivesItsShape()} — a derived shape has no pair count until the
     * lift has been sized against the seats it is carrying.
     */
    public int playerShuttlePairCount() {
        return Math.multiplyExact(dropZoneCount, shuttlePairsPerZone);
    }

    public ConquestArrivalConfig withDropZoneCount(int count) {
        return new ConquestArrivalConfig(count, shuttlePairsPerZone,
                timingJitterSec, landingShare);
    }

    public ConquestArrivalConfig withShuttlePairsPerZone(int count) {
        return new ConquestArrivalConfig(dropZoneCount, count,
                timingJitterSec, landingShare);
    }

    public ConquestArrivalConfig withTimingJitterSec(float seconds) {
        return new ConquestArrivalConfig(dropZoneCount, shuttlePairsPerZone,
                seconds, landingShare);
    }

    public ConquestArrivalConfig withLandingShare(LandingShare share) {
        return new ConquestArrivalConfig(dropZoneCount, shuttlePairsPerZone,
                timingJitterSec, share);
    }
}
