package com.dillon.starsectormarines.battle.decision;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * One slice of per-tick AI logic for a unit. The unit-update pass dispatches to
 * a behavior based on the unit's ROLE component (with fall-back as a pre-dispatch
 * override). Each behavior is stateless across ticks — all per-unit state lives in
 * world components keyed by the unit's {@code long} entity id — so a single instance
 * can service every unit on the field.
 *
 * <p>Implementations may read {@link BattleSimulation} freely (grid, units,
 * occupancy map) and mutate the passed unit by its id; structural sim mutations go
 * through the sim's public surface ({@link BattleSimulation#fireShot},
 * {@link BattleSimulation#setPath}, etc.). Keeps the dispatch table simple
 * and the role-specific logic isolated for future behavior-tree growth.
 */
public interface UnitBehavior {
    /** Runs one tick of AI for the unit with entity id {@code u}. */
    void update(long u, BattleSimulation sim);
}
