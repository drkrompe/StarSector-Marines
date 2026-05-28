package com.dillon.starsectormarines.battle.equipment;

import com.dillon.starsectormarines.battle.unit.UnitRole;

import com.dillon.starsectormarines.battle.command.objective.Objective;

/**
 * A dropped equipment kit on the battlefield. Spawned when a {@link UnitRole#PLANTER}
 * (or {@link UnitRole#KIT_RETRIEVER} on the way back with a kit) dies before
 * completing their plant — the kit lands at their last cell, carrying the
 * reference to the {@link Objective} they were tasked with.
 *
 * <p>Any marine who walks onto {@link #cellX}/{@link #cellY} consumes the drop
 * and inherits the objective as a fresh PLANTER. The AI nominates the nearest
 * alive combatant as KIT_RETRIEVER so kits don't sit untouched on the map.
 *
 * <p>One drop per kit; {@link #consumed} flips when the kit is picked up, after
 * which the sim removes it from the active list on the next sweep.
 */
public final class EquipmentDrop {

    public final int cellX;
    public final int cellY;
    public final Objective objective;
    public boolean consumed = false;

    public EquipmentDrop(int cellX, int cellY, Objective objective) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.objective = objective;
    }
}
