package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.ai.InfantryCohesion;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * <b>Squad posture: rejoin the squad centroid.</b> Each member paths toward
 * the others-only centroid using
 * {@link InfantryCohesion#cohesionOverride} — when the helper
 * returns null (already within cohesion radius or solo squad), the member
 * reports {@link ActionStatus#SUCCESS} immediately.
 *
 * <p>Empty preconditions: the planner picks this whenever a downstream
 * posture requires {@link Predicate#WITHIN_COHESION_RADIUS} (today: only
 * {@link ApproachPosture}) and the snapshot says the squad is scattered.
 */
public final class RegroupPosture implements Action {

    public static final RegroupPosture INSTANCE = new RegroupPosture();

    private static final WorldState PRE = WorldState.EMPTY;
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.WITHIN_COHESION_RADIUS, true);

    private RegroupPosture() {}

    @Override public String name() { return "Regroup"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 3f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        int[] dest = InfantryCohesion.cohesionOverride(member, sim);
        if (dest == null) {
            // Already within cohesion radius (or solo squad) — done for this member.
            return ActionStatus.SUCCESS;
        }

        if (member.moveProgress == 0f) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.getCellX(), member.getCellY(), dest[0], dest[1], sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);

        return ActionStatus.RUNNING;
    }
}
