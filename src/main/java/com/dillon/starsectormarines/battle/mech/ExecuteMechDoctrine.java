package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

/**
 * One shared mech-plan step that dispatches every member through its own
 * effective doctrine. This prevents the squad-level goal tie-break from
 * starving another role in a mixed lance.
 */
public final class ExecuteMechDoctrine implements Action {

    public static final ExecuteMechDoctrine INSTANCE = new ExecuteMechDoctrine();

    private static final WorldState EFFECTS = WorldState.EMPTY
            .with(Predicate.ENEMY_DAMAGED, true);

    private ExecuteMechDoctrine() {}

    @Override public String name() { return "ExecuteMechDoctrine"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return EFFECTS; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        MechLoadoutComponent loadout = sim.world().mechLoadout(member);
        if (loadout == null) return ActionStatus.FAILURE;
        MechAssignmentBoundary.observeAssignment(member, squad, sim);
        return actionFor(loadout.effectiveRole()).execute(member, squad, sim);
    }

    static Action actionFor(MechRole role) {
        return switch (role) {
            case ASSAULT -> BreachAndAssault.INSTANCE;
            case ARMORED_SUPPORT -> BackstopAssignedSquad.INSTANCE;
            case LR_SUPPORT -> OverwatchKillZone.INSTANCE;
            case BALANCED -> EngageAtCurrentBand.INSTANCE;
        };
    }
}
