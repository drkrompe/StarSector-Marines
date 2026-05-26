package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.ai.InfantryCohesion;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * <b>Squad posture: close to firing range.</b> Pure movement — no firing
 * happens here ({@link EngagePosture} owns that branch). Picked by the
 * planner when the squad doesn't yet have LOS + range to a target but does
 * have a target acquired.
 *
 * <p>Precondition {@link Predicate#WITHIN_COHESION_RADIUS} is what makes the
 * planner insert {@link RegroupPosture} ahead of this when the squad is
 * scattered — without that link, cohesion would never be planned for.
 *
 * <p>Per-member execution: each member paths to a firing position with
 * cohesion override inline. Returns {@link ActionStatus#SUCCESS} the moment
 * a member arrives in range + LOS, advancing the squad plan to Engage. Other
 * still-moving members re-enter {@link EngagePosture}'s out-of-range branch
 * next tick — they keep walking, just under a different posture banner.
 */
public final class ApproachPosture implements Action {

    public static final ApproachPosture INSTANCE = new ApproachPosture();

    private static final WorldState PRE = WorldState.EMPTY
            .with(Predicate.HAS_TARGET, true)
            .with(Predicate.WITHIN_COHESION_RADIUS, true);
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.HAS_LOS_TO_TARGET, true)
            .with(Predicate.IN_RANGE_OF_TARGET, true);

    private ApproachPosture() {}

    @Override public String name() { return "Approach"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 2f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        // Cooldown ticks are handled by GoapInfantryBehavior.prepareForAction
        // before this method runs — see InfantryUnitPrep.

        // Mid-approach re-target: drop the current target when dead, gone, or
        // a closer visible enemy now exists. The user-reported failure was a
        // squad walking past a close mech to engage a distant turret because
        // member.target was locked at approach start; shouldKeepPursuing's
        // closer-visible-target check is what unsticks that case.
        Unit target = sim.targetOf(member);
        if (target == null
                || !TacticalScoring.shouldKeepPursuing(member, target, sim)) {
            target = TacticalScoring.findBestTarget(member, sim);
            member.setTarget(target);
        }
        if (target == null) return ActionStatus.FAILURE;

        float dist = TacticalScoring.cellDistance(member.getCellX(), member.getCellY(),
                target.getCellX(), target.getCellY());
        boolean inRange = dist <= member.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(member.getCellX(), member.getCellY(),
                target.getCellX(), target.getCellY());
        if (inRange && visible) return ActionStatus.SUCCESS;

        if (member.moveProgress == 0f) {
            int[] dest = InfantryCohesion.cohesionOverride(member, sim);
            if (dest == null) dest = TacticalScoring.findFiringPosition(member, target, sim);
            if (dest == null) {
                // No reachable firing position OR vantage point exists for the
                // current target — geometrically unreachable from here. Drop
                // the target so next tick's findBestTarget picks something the
                // unit can actually engage. Returning RUNNING (not FAILURE)
                // keeps the squad-level Approach plan alive; the re-acquire
                // happens on the next per-member tick.
                member.targetId = 0L;
                return ActionStatus.RUNNING;
            }
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.getCellX(), member.getCellY(), dest[0], dest[1], sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);

        return ActionStatus.RUNNING;
    }
}
