package com.dillon.starsectormarines.battle.decision;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Fall-back state — unit was recently hit and is breaking contact. Paths
 * toward an out-of-LOS cell, then holds until {@code world.fallbackTimer(id)}
 * expires. Applies to every role (the dispatch in
 * {@link BattleSimulation#updateUnit} routes here whenever the timer is
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
        if (sim.world().cellX(u) == fx && sim.world().cellY(u) == fy) {
            sim.clearPath(u);
            sim.world().setMoveProgress(u, 0f);
            sim.world().setRenderPos(u, sim.world().cellX(u), sim.world().cellY(u));
            return;
        }
        if (sim.world().moveProgress(u) == 0f) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), sim.world().cellX(u), sim.world().cellY(u), fx, fy, sim.getOccupancyMap()));
        }
        sim.advanceMovement(u);
    }
}
