package com.dillon.starsectormarines.battle.decision;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;

/**
 * One slice of per-tick AI logic for a unit. {@link BattleSimulation#updateUnit}
 * dispatches to a behavior based on {@link Entity#role} (with fall-back as a
 * pre-dispatch override). Each behavior is stateless across ticks — all
 * per-unit state lives on {@link Entity} itself — so a single instance can
 * service every unit on the field.
 *
 * <p>Implementations may read {@link BattleSimulation} freely (grid, units,
 * occupancy map) and mutate the passed unit; structural sim mutations go
 * through the sim's public surface ({@link BattleSimulation#fireShot},
 * {@link BattleSimulation#setPath}, etc.). Keeps the dispatch table simple
 * and the role-specific logic isolated for future behavior-tree growth.
 */
public interface UnitBehavior {
    void update(Entity u, BattleSimulation sim);
}
