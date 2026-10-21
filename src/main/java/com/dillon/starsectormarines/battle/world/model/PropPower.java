package com.dillon.starsectormarines.battle.world.model;

/**
 * How well a battle map's machinery is powered, at a point: what a powered prop reads to decide how fast it runs.
 *
 * <p>Each kind of map answers for itself. A ship's deck answers from her power core; a fort from whether its
 * generator hall still runs; a colony depot or a factory from whatever its mission says about the grid. A map that
 * says nothing is fully powered, so a machine on an ordinary battle map runs. Presentation only: nothing tactical
 * reads this.
 */
@FunctionalInterface
public interface PropPower {

    /** Everything fully powered: the answer for a map that has no opinion. */
    PropPower FULL = (cellX, cellY) -> 0f;

    /**
     * The power deficit at a point in cells: 0 with the power fully up, 1 with none, and between while a change is
     * still reaching this part of the map.
     */
    float deficitAt(float cellX, float cellY);
}
