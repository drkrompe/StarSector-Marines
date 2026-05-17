package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Random;

/**
 * Narrow contract the {@link AirSystem} (and, eventually, the fighter and
 * air-base subsystems) needs from the surrounding simulation. Kept small on
 * purpose: every method here is a real call site, not a "the air system might
 * want this someday" surface. Adding to it should require a real need.
 *
 * <p>{@link com.dillon.starsectormarines.battle.BattleSimulation} implements
 * this directly. Air-system unit tests can supply a stub that records calls
 * without dragging in the full sim.
 */
public interface AirSimContext {

    /** The navigation grid — used for walkability/in-bounds checks when picking a deboard cell. */
    NavigationGrid getGrid();

    /** Shared RNG, threaded through so deterministic-seed runs produce identical battles. */
    Random getRng();

    /** True if any alive ground unit currently stands on the given cell. Walls are not "occupied" here — that's a grid concern. */
    boolean isCellOccupied(int x, int y);

    /**
     * Allocates a fresh, unique id for a deboarded marine. The {@code m0, m1, …}
     * pattern is internal to the sim — air systems shouldn't reach for the
     * counter directly.
     */
    String nextMarineId();

    /**
     * Creates a new squad with {@code leader} as its leader and returns the
     * assigned squad id. The squad joins the sim's squad map; subsequent
     * deboards from the same shuttle pass that id back to put marines into
     * the existing squad.
     */
    int mintSquad(Faction faction, Unit leader);

    /** Inserts a freshly-deboarded marine into the simulation's unit roster. */
    void addUnit(Unit u);
}
