package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.InfantryCombatantBehavior;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Stage 1 parity action: per-member, path toward the others-only centroid
 * to rejoin the squad. Wraps the same math as
 * {@link InfantryCombatantBehavior#cohesionOverride} — when the helper
 * returns null (already within {@link InfantryCombatantBehavior#COHESION_RADIUS}
 * or solo squad), this action reports {@link ActionStatus#SUCCESS} for that
 * member.
 *
 * <p>Empty preconditions — the planner picks this whenever an upstream
 * action requires {@link Predicate#WITHIN_COHESION_RADIUS}
 * ({@link MoveToFiringPositionAction} today) and the snapshot says the squad
 * isn't cohered.
 */
public final class MaintainCohesionAction implements Action {

    public static final MaintainCohesionAction INSTANCE = new MaintainCohesionAction();

    private static final WorldState PRE = WorldState.EMPTY;
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.WITHIN_COHESION_RADIUS, true);

    private MaintainCohesionAction() {}

    @Override public String name() { return "MaintainCohesion"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 3f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        int[] dest = InfantryCombatantBehavior.cohesionOverride(member, sim);
        if (dest == null) {
            // Already within cohesion radius (or solo squad) — done for this member.
            return ActionStatus.SUCCESS;
        }

        if (member.moveProgress == 0f) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.cellX, member.cellY, dest[0], dest[1], sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);

        return ActionStatus.RUNNING;
    }
}
