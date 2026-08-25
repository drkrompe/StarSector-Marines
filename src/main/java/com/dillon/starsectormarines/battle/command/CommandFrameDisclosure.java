package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Trusted, battle-owned projection from the live simulation into one frozen
 * strategy frame. Implementations must be stateless and must not retain the
 * supplied view. The disclosure is installed beside a strategy; it is never
 * handed to the strategy itself.
 */
@FunctionalInterface
public interface CommandFrameDisclosure<F extends CommandFrame> {

    F freeze(BattleView sim, Faction perspective,
             CommandTopology topology,
             CommandAssignmentSnapshot assignments);
}
