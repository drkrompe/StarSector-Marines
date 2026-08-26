package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;

/** Mission-authored policy for turning committed lift into ground arrivals. */
public enum MarineArrivalPolicy {
    /** Historical behavior: each craft lands independently with a full hold. */
    INDEPENDENT_FULL_LOAD,
    /** Two craft share an arrival area and each delivers half a 12-marine squad. */
    PAIRED_HALF_SQUAD;

    public static MarineArrivalPolicy defaultFor(MissionType type) {
        return type == MissionType.CONQUEST
                ? PAIRED_HALF_SQUAD : INDEPENDENT_FULL_LOAD;
    }

    public int seatsPerSortie(ShuttleType type) {
        if (this == PAIRED_HALF_SQUAD) {
            return Math.min(6, type.capacity);
        }
        return type.capacity;
    }
}
