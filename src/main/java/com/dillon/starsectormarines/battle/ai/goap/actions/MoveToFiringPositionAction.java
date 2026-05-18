package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.InfantryCombatantBehavior;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Stage 1 parity action: per-member, path toward a firing position. No firing
 * happens here — {@link EngageVisibleAction} owns the in-range fire branch.
 * Precondition requires {@link Predicate#WITHIN_COHESION_RADIUS} so the
 * planner inserts {@link MaintainCohesionAction} ahead of this when the
 * squad is scattered.
 *
 * <p>Returns {@link ActionStatus#SUCCESS} the moment a member arrives in
 * range with LOS, advancing the plan to the engage step. Other still-moving
 * members re-enter {@link EngageVisibleAction}'s out-of-range branch on the
 * next tick — they keep walking. This mirrors the pre-GOAP behavior where
 * the first marine in range opens fire while squadmates close.
 */
public final class MoveToFiringPositionAction implements Action {

    public static final MoveToFiringPositionAction INSTANCE = new MoveToFiringPositionAction();

    private static final WorldState PRE = WorldState.EMPTY
            .with(Predicate.HAS_TARGET, true)
            .with(Predicate.WITHIN_COHESION_RADIUS, true);
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.HAS_LOS_TO_TARGET, true)
            .with(Predicate.IN_RANGE_OF_TARGET, true);

    private MoveToFiringPositionAction() {}

    @Override public String name() { return "MoveToFiringPosition"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 2f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        if (member.target == null || !member.target.isAlive()) {
            member.target = TacticalScoring.findBestTarget(member, sim);
        }
        if (member.target == null) return ActionStatus.FAILURE;

        // Cooldowns keep ticking during the move so the marine isn't sitting
        // on a stale cooldown when EngageVisible takes over.
        if (member.cooldownTimer > 0f) member.cooldownTimer -= BattleSimulation.TICK_DT;
        if (member.secondaryCooldownTimer > 0f) member.secondaryCooldownTimer -= BattleSimulation.TICK_DT;

        float dist = TacticalScoring.cellDistance(member.cellX, member.cellY,
                member.target.cellX, member.target.cellY);
        boolean inRange = dist <= member.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(member.cellX, member.cellY,
                member.target.cellX, member.target.cellY);
        if (inRange && visible) return ActionStatus.SUCCESS;

        if (member.moveProgress == 0f) {
            int[] dest = InfantryCombatantBehavior.cohesionOverride(member, sim);
            if (dest == null) dest = TacticalScoring.findFiringPosition(member, member.target, sim);
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.cellX, member.cellY, dest[0], dest[1], sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);

        return ActionStatus.RUNNING;
    }
}
