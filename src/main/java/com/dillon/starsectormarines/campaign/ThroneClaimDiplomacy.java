package com.dillon.starsectormarines.campaign;

/** Port for replay-safe vanilla diplomacy consequences after a throne claim lands. */
public interface ThroneClaimDiplomacy {

    enum Result {
        APPLIED,
        ALREADY_APPLIED,
        RETRY,
        REJECTED
    }

    Result apply(String sourceFactionId, String resultFactionId);
}
