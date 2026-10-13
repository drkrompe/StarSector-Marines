package com.dillon.starsectormarines.battle.world.gen;

/**
 * How a mission wants its declared forts placed. Where a fort goes is a mission-design decision as much as how many
 * there are, so it is carried on the mission's {@link GroundDefences} declaration and never inferred by map generation.
 */
public enum FortSitingPolicy {

    /**
     * Forts take whatever open ground the city has left, deep band first and nearest the map centre. No relationship to
     * the battle's lines of attack is intended; this is what scenes, debug and every mission that has not opted in get.
     */
    LEFTOVER_GROUND,

    /**
     * The fort is a standing barrier on the way in. A shield fort stands as near the keep and the garrison compounds as
     * ground allows, so its dome covers what the garrison holds most dear; battery forts stand on the approach between
     * the landing and the keep, spread across it, so the attack has to pass inside their envelope and deal with them.
     * Falls back to {@link #LEFTOVER_GROUND} on a map that states no landing or no keep.
     */
    COVER_THE_APPROACH
}
