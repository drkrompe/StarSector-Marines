package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

import java.util.Collections;

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
        if (u.cellX == fx && u.cellY == fy) {
            sim.setPath(u, Collections.emptyList());
            u.moveProgress = 0f;
            u.renderX = u.cellX;
            u.renderY = u.cellY;
            return;
        }
        if (u.moveProgress == 0f) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), u.cellX, u.cellY, fx, fy, sim.getOccupancyMap()));
        }
        sim.advanceMovement(u);
    }
}
