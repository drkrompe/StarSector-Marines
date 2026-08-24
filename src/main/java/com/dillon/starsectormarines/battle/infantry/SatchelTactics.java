package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.satchel.SatchelChargeService;
import com.dillon.starsectormarines.battle.sim.BattleControl;

/** Faction-neutral escape override for friendlies who know an armed satchel's footprint. */
public final class SatchelTactics {

    private static final float SAFETY_MARGIN = 0.9f;
    private static final int ESCAPE_DIRECTIONS = 16;

    private SatchelTactics() {}

    /** Moves one infantry unit away from the nearest friendly armed charge. */
    public static boolean evadeFriendlyCharge(long unit, BattleControl sim) {
        SatchelChargeService.ChargeView hazard = sim.satchelCharges().nearestFriendlyHazard(
                sim.identity().faction(unit), sim.world().x(unit), sim.world().y(unit),
                SAFETY_MARGIN);
        if (hazard == null) return false;
        NavigationGrid grid = sim.getGrid();
        int fromX = sim.world().cellX(unit);
        int fromY = sim.world().cellY(unit);
        float radius = hazard.blastRadius() + SAFETY_MARGIN + 0.75f;
        int[] bestPath = null;
        float bestDistanceSq = -1f;
        for (int i = 0; i < ESCAPE_DIRECTIONS; i++) {
            float angle = (float) (Math.PI * 2.0 * i / ESCAPE_DIRECTIONS);
            int x = (int) Math.floor(hazard.x() + 0.5f + Math.cos(angle) * radius);
            int y = (int) Math.floor(hazard.y() + 0.5f + Math.sin(angle) * radius);
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
            int[] path = GridPathfinder.findPath(grid, fromX, fromY, x, y,
                    sim.getOccupancyMap());
            if (path.length == 0) continue;
            float dx = x + 0.5f - hazard.x();
            float dy = y + 0.5f - hazard.y();
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq > bestDistanceSq
                    || distanceSq == bestDistanceSq
                    && (bestPath == null || path.length < bestPath.length)) {
                bestPath = path;
                bestDistanceSq = distanceSq;
            }
        }
        if (bestPath == null) {
            sim.clearPath(unit);
            return true;
        }
        sim.setPath(unit, bestPath);
        sim.advanceMovement(unit);
        return true;
    }
}
