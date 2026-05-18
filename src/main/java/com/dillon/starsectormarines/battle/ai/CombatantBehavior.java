package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Unit;

/**
 * Default combat loop dispatcher. Delegates to {@link MechCombatantBehavior}
 * for units carrying a {@link Unit#mech} loadout and to
 * {@link InfantryCombatantBehavior} for everyone else. Stays as the public
 * entry so callers ({@link PatrolBehavior}, {@link GarrisonBehavior},
 * {@link PlanterBehavior}, {@link KitRetrieverBehavior}, and the role
 * dispatch in {@code BattleSimulation.behaviorFor}) don't need to know
 * which flavor a given unit needs.
 */
public final class CombatantBehavior implements UnitBehavior {

    public static final CombatantBehavior INSTANCE = new CombatantBehavior();

    private CombatantBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        if (u.mech != null) {
            MechCombatantBehavior.INSTANCE.update(u, sim);
        } else {
            InfantryCombatantBehavior.INSTANCE.update(u, sim);
        }
    }
}
