package com.dillon.starsectormarines.campaign;

/** Narrow port around the irreversible vanilla faction/market mutation. */
public interface ThroneClaimWriteback {

    enum Result {
        APPLIED,
        ALREADY_APPLIED,
        RETRY,
        REJECTED
    }

    /**
     * Ensures {@code marketId} belongs to {@code resultFactionId}, checking the
     * postcondition before mutation so repeated calls are idempotent.
     */
    Result apply(String sourceFactionId, String resultFactionId, String marketId);
}
