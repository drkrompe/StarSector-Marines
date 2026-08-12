package com.dillon.starsectormarines.battle.decision;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Fall-back state — unit was recently hit and is breaking contact. Paths
 * toward an out-of-LOS cell, then holds until {@code world.fallbackTimer(id)}
 * expires. Applies to every role (the dispatch in
 * {@code UnitUpdateSystem#updateUnit} routes here whenever the timer is
 * positive, regardless of {@code role}).
 */
public final class FallbackBehavior implements UnitBehavior {

    public static final FallbackBehavior INSTANCE = new FallbackBehavior();

    private FallbackBehavior() {}

    @Override
    public void update(long u, BattleSimulation sim) {
        sim.world().setFallbackTimer(u, sim.world().fallbackTimer(u) - BattleSimulation.TICK_DT);
        int fx = sim.world().fallbackCellX(u);
        int fy = sim.world().fallbackCellY(u);
        if (sim.movement().atCell(u, fx, fy)) {
            sim.clearPath(u);
            sim.world().setMoveProgress(u, 0f);
            sim.world().setRenderPos(u, sim.world().cellX(u), sim.world().cellY(u));
            return;
        }
        if (sim.movement().mayRepath(u)) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), sim.world().cellX(u), sim.world().cellY(u), fx, fy, sim.getOccupancyMap()));
        }
        sim.advanceMovement(u);
    }
}
