package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.goap.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.ai.goap.GoapMechBehavior;

/**
 * Default combat loop dispatcher. Two-way: {@link GoapMechBehavior} for
 * units carrying a {@link Unit#mech} loadout, {@link GoapInfantryBehavior}
 * for everyone else. Both paths run through the squad-level GOAP planner
 * with their own goal + action libraries (see
 * {@code roadmap/ai/14-mech-stage1.md}).
 *
 * <p>Stays as the public entry so callers ({@link PatrolBehavior},
 * {@link GarrisonBehavior}, {@link KitRetrieverBehavior}, and the role
 * dispatch in {@code BattleSimulation.behaviorFor} — which also routes
 * {@code PLANTER} here so the planter participates in the squad-plan
 * cordon slot rather than running its own per-unit path) don't need to know
 * which flavor a given unit needs.
 */
public final class CombatantBehavior implements UnitBehavior {

    public static final CombatantBehavior INSTANCE = new CombatantBehavior();

    private CombatantBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        if (u.mech != null) {
            GoapMechBehavior.INSTANCE.update(u, sim);
        } else {
            GoapInfantryBehavior.INSTANCE.update(u, sim);
        }
    }
}
