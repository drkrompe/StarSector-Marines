package com.dillon.starsectormarines.battle.turret;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;

/**
 * No-op per-tick behavior for static {@link com.dillon.starsectormarines.battle.unit.UnitRole#STRUCTURE}
 * units (drone hubs etc.). The unit takes damage and dies through the same
 * {@code Entity.hp} pipeline as any other combatant; nothing in this behavior
 * needs to fire, move, or acquire targets.
 */
public final class StructureBehavior implements UnitBehavior {

    public static final StructureBehavior INSTANCE = new StructureBehavior();

    private StructureBehavior() {}

    @Override
    public void update(Entity u, BattleSimulation sim) {
        // intentional no-op
    }
}
