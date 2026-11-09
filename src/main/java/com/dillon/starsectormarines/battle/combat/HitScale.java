package com.dillon.starsectormarines.battle.combat;

/**
 * A scale on one hit's damage after cover, by who struck whom: a unit whose plate depends on a stance of its own,
 * such as the braced vault frame, or whose own parts may take the hit, such as a walker's knees
 * (`ruins-guardians.md`), answers it. Asked once per resolved hit, in the serial damage
 * pipeline, so an answer may change the unit's own state (a hit from behind throwing a brace off).
 */
@FunctionalInterface
public interface HitScale {

    /** No unit scales its hits. */
    HitScale NONE = (targetId, attackerId, damage) -> 1f;

    /**
     * The scale on a hit of {@code damage} (after cover) by {@code attackerId} on {@code targetId}; 1 for an ordinary
     * hit, 0 when a part of the target that the unit keeps itself (a walker's knee) took the hit instead.
     */
    float scale(long targetId, long attackerId, float damage);
}
