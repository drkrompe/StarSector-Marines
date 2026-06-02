package com.dillon.starsectormarines.battle.decision;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Fall-back state — unit was recently hit and is breaking contact. Paths
 * toward an out-of-LOS cell, then holds until {@link Unit#getFallbackTimer()}
 * expires. Applies to every role (the dispatch in
 * {@link BattleSimulation#updateUnit} routes here whenever the timer is
 * positive, regardless of {@link Unit#role}).
 */
public final class FallbackBehavior implements UnitBehavior {

    public static final FallbackBehavior INSTANCE = new FallbackBehavior();

    private FallbackBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        sim.world().setFallbackTimer(u.entityId, sim.world().fallbackTimer(u.entityId) - BattleSimulation.TICK_DT);
        int fx = sim.world().fallbackCellX(u.entityId);
        int fy = sim.world().fallbackCellY(u.entityId);
        if (sim.world().cellX(u.entityId) == fx && sim.world().cellY(u.entityId) == fy) {
            sim.clearPath(u);
            sim.world().setMoveProgress(u.entityId, 0f);
            u.setRenderPos(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId));
            return;
        }
        if (sim.world().moveProgress(u.entityId) == 0f) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), fx, fy, sim.getOccupancyMap()));
        }
        sim.advanceMovement(u);
    }
}
