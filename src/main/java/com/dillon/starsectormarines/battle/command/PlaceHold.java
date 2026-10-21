package com.dillon.starsectormarines.battle.command;

/**
 * How a party believes a place stands (`garrisonables.md`, place directives): its own squads in it, believed hostiles
 * in it, both, or neither. Belief only: a party that has seen nobody in a place reads it as empty, whoever is there.
 */
public enum PlaceHold {
    /** Own squads stand in it and nobody hostile is believed there. */
    OWN,
    /** Believed hostiles are in it and none of the party's own squads. */
    HOSTILE,
    /** Both. */
    CONTESTED,
    /** Neither. */
    EMPTY;

    static PlaceHold of(boolean own, boolean hostile) {
        return own ? hostile ? CONTESTED : OWN : hostile ? HOSTILE : EMPTY;
    }
}
