package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.unit.Faction;

/** Whether {@code side} has somebody other than the shooter looking at {@code target} right now ({@link SideObservers}). */
@FunctionalInterface
public interface SideSpotting {

    boolean spotted(Faction side, long target);

    /** Nobody is looking: an indirect-fire hull fires only on what it sees itself. */
    SideSpotting NONE = (side, target) -> false;
}
