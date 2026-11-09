package com.dillon.starsectormarines.battle.combat;

/**
 * A scale on one hit's damage after cover, by who struck whom: a unit whose plate depends on a stance of its own,
 * such as the braced vault frame (`ruins-guardians.md`), answers it. Asked once per resolved hit, in the serial damage
 * pipeline, so an answer may change the unit's own state (a hit from behind throwing a brace off).
 */
@FunctionalInterface
public interface HitScale {

    /** No unit scales its hits. */
    HitScale NONE = (targetId, attackerId) -> 1f;

    /** The scale on a hit by {@code attackerId} on {@code targetId}; 1 for an ordinary hit. */
    float scale(long targetId, long attackerId);
}
