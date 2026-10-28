package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleType;

/** Mission-authored policy for turning committed lift into ground arrivals. */
public enum MarineArrivalPolicy {
    /** Historical behavior: each craft lands independently with a full hold. */
    INDEPENDENT_FULL_LOAD,
    /**
     * Two craft share an arrival area and each delivers half a 12-marine squad. Only Conquest's
     * paired descent lands half-squads; a company boat elsewhere seats her cabin
     * ({@code DetachmentResolver.companySeatsPerTrip}).
     */
    PAIRED_HALF_SQUAD,
    /** Selected company already occupies the secured site; boats remain in reserve. */
    SECURED_GROUND;

    public static MarineArrivalPolicy defaultFor(MissionType type) {
        return type == MissionType.CONQUEST
                ? PAIRED_HALF_SQUAD : INDEPENDENT_FULL_LOAD;
    }

    /** Seats one sortie of {@code craft} fills: its own seats, or a half-squad when paired. */
    public int seatsPerSortie(Airframe craft) {
        if (this == PAIRED_HALF_SQUAD) {
            return Math.min(6, craft.seats());
        }
        return craft.seats();
    }

    /** Resolves the ground-battle craft from the committed campaign lift. */
    public ShuttleType deliveryCraft(ShuttleType committedLift) {
        return this == PAIRED_HALF_SQUAD
                ? ShuttleType.AEROSHUTTLE : committedLift;
    }
}
