package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * Something that looks at the world once per tick, before the tick advances.
 *
 * <p>The seam between the run loop and its instruments: an order trace, a
 * casualty tally, a distance meter all implement this and are attached to the
 * run rather than baked into it. Observers are called after the scripted
 * player has issued that tick's orders and before {@code sim.advance}, so what
 * they see is the state the tick will act on.
 */
@FunctionalInterface
public interface TickObserver {

    void observe(BattleSimulation sim, int tick);
}
