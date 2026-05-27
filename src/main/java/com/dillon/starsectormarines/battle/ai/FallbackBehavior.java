package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Fall-back state — unit was recently hit and is breaking contact. Paths
 * toward an out-of-LOS cell, then holds until {@link Unit#fallbackTimer}
 * expires. Applies to every role (the dispatch in
 * {@link BattleSimulation#updateUnit} routes here whenever the timer is
 * positive, regardless of {@link Unit#role}).
 */
public final class FallbackBehavior implements UnitBehavior {

    public static final FallbackBehavior INSTANCE = new FallbackBehavior();

    private FallbackBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        u.fallbackTimer -= BattleSimulation.TICK_DT;
        int fx = u.fallbackCellX;
        int fy = u.fallbackCellY;
        if (u.getCellX() == fx && u.getCellY() == fy) {
            sim.clearPath(u);
            u.setMoveProgress(0f);
            u.setRenderPos(u.getCellX(), u.getCellY());
            return;
        }
        if (u.getMoveProgress() == 0f) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), u.getCellX(), u.getCellY(), fx, fy, sim.getOccupancyMap()));
        }
        sim.advanceMovement(u);
    }
}
