package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
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
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 3f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleControl sim) {
        int[] dest = InfantryCohesion.cohesionOverride(member, sim);
        if (dest == null) {
            // Already within cohesion radius (or solo squad) — done for this member.
            return ActionStatus.SUCCESS;
        }

        if (sim.world().moveProgress(member.entityId) == 0f) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member.entityId), sim.world().cellY(member.entityId), dest[0], dest[1], sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);

        return ActionStatus.RUNNING;
    }
}
