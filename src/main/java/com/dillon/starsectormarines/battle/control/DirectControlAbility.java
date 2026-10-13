package com.dillon.starsectormarines.battle.control;

/** Explicit one-shot equipment requests for the exact controlled Marine. */
public enum DirectControlAbility {
    SHIELD,
    SMOKE,
    /** A tender pilot's X key: withdraw every unfinished build site. */
    CANCEL_BUILDS
}
