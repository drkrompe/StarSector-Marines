package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * Where an allied force's fresh machines set out from (`allied-tools.md`, logistics base): a vehicle park, a shed, a
 * rear edge. A wave due while the depot cannot send waits for it rather than appearing somewhere else, so losing the
 * base is what stops the reinforcements.
 */
public interface AlliedDepot {

    /** The cell a machine sent now sets out from, or {@code null} while the depot cannot send (overrun, cut off). */
    int[] dispatchCell(BattleSimulation sim);
}
