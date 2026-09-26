package com.dillon.starsectormarines.battle.nav;

/** Pending terrain proof holds the requested intent; only FAILED permits choosing a fallback. */
public enum PathRequestStatus {
    READY, PENDING, FAILED
}
