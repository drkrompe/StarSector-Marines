package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.fire.FireEscape;
import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;

/**
 * A marine in the fire's way gets out of it before doing anything else
 * (`fire-nouns.md`, Pathing): in a burning cell, or on fuel near one
 * ({@link FireEscape#needsToMove}). He walks on along a route that already
 * ends somewhere safe, and otherwise goes to {@link FireEscape#refuge}. Either
 * way he neither shoots nor takes up a new step until he is out of the way. Out
 * of it, his movement waits at the fire's edge rather than step back in
 * ({@link FireEscape#waitsAtEdge}, applied where every route is walked).
 *
 * <p>{@code -Dbattle.fire.stepOut=false} is the control ({@link FireEscape#PROPERTY}).
 */
final class FireStepOut {

    private FireStepOut() { }

    static boolean stepOut(long unit, BattleControl sim) {
        if (!FireEscape.enabled()) return false;
        FireField field = sim.fireFieldIfPresent();
        if (field == null || field.burningCount() == 0 || !sim.world().hasMovement(unit)) return false;
        int x = sim.world().cellX(unit), y = sim.world().cellY(unit);
        if (!FireEscape.needsToMove(field, x, y)) return false;
        int[] path = sim.world().path(unit);
        int next = sim.world().pathIdx(unit);
        if (next < Paths.cellCount(path) && FireEscape.isSafe(field, Paths.destX(path), Paths.destY(path))
                && !field.isBurning(Paths.cellX(path, next), Paths.cellY(path, next))) {
            sim.advanceMovement(unit);
            return true;
        }
        NavigationGrid grid = sim.getGrid();
        int refuge = FireEscape.refuge(grid, field, x, y);
        if (refuge < 0) return false;
        int[] out = GridPathfinder.findPath(grid, x, y, refuge % grid.getWidth(), refuge / grid.getWidth(),
                sim.getOccupancyMap());
        if (Paths.isEmpty(out)) return false;
        sim.setPath(unit, out);
        sim.advanceMovement(unit);
        return true;
    }
}
