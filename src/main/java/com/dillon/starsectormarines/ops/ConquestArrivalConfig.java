package com.dillon.starsectormarines.ops;

/**
 * Mission-authored shape and timing variance for paired Conquest arrivals.
 * Drop zones are physical beachheads; each player pair assigned to one of
 * those zones cycles until its share of the selected company has landed.
 */
public record ConquestArrivalConfig(
        int dropZoneCount,
        int shuttlePairsPerZone,
        float timingJitterSec) {

    public static final ConquestArrivalConfig DEFAULT =
            new ConquestArrivalConfig(3, 1, 1f);
    public static final ConquestArrivalConfig LEGACY =
            new ConquestArrivalConfig(1, 1, 0f);

    public ConquestArrivalConfig {
        if (dropZoneCount < 1) {
            throw new IllegalArgumentException("dropZoneCount must be positive");
        }
        if (shuttlePairsPerZone < 1) {
            throw new IllegalArgumentException("shuttlePairsPerZone must be positive");
        }
        if (!Float.isFinite(timingJitterSec) || timingJitterSec < 0f) {
            throw new IllegalArgumentException(
                    "timingJitterSec must be finite and non-negative");
        }
    }

    public static ConquestArrivalConfig defaultFor(MissionType type) {
        return type == MissionType.CONQUEST ? DEFAULT : LEGACY;
    }

    public int playerShuttlePairCount() {
        return Math.multiplyExact(dropZoneCount, shuttlePairsPerZone);
    }

    public ConquestArrivalConfig withDropZoneCount(int count) {
        return new ConquestArrivalConfig(count, shuttlePairsPerZone,
                timingJitterSec);
    }

    public ConquestArrivalConfig withShuttlePairsPerZone(int count) {
        return new ConquestArrivalConfig(dropZoneCount, count,
                timingJitterSec);
    }

    public ConquestArrivalConfig withTimingJitterSec(float seconds) {
        return new ConquestArrivalConfig(dropZoneCount, shuttlePairsPerZone,
                seconds);
    }
}
