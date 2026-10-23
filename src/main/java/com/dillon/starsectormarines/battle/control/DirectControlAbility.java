package com.dillon.starsectormarines.battle.control;

/** Explicit one-shot equipment requests for the exact controlled Marine. */
public enum DirectControlAbility {
    SHIELD,
    SMOKE,
    /** Throw the carried hand grenade at the cursor; the right button only, when it is the pick. */
    GRENADE,
    /** A tender pilot's X key: withdraw every unfinished build site. */
    CANCEL_BUILDS,
    /** A warship pilot's F key: a fast, low pass from the hull to the cursor. */
    LOW_PASS,
    /** Reload the controlled Marine's magazine-fed primary before it runs dry. */
    RELOAD
}
