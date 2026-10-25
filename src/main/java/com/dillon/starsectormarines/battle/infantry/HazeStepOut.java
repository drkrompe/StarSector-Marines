package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * A marine standing in a Bombard's spore haze steps out of it (`bombard.md`, Counters): the haze spoils his aim and
 * stings, and hides nothing, so the answer is to leave it. He walks on along a route that already ends outside it,
 * and otherwise goes to the nearest clear cell ({@link #refuge}). A marine with a Rav in melee reach stays and
 * fights: turning his back on a blade to get out of a sting is the worse trade.
 */
final class HazeStepOut {

    /** A swarm fighter this close is a fight he does not walk out of. */
    static final float MELEE_REACH = 2.5f;
    /** How far he looks for clear ground; a haze is three cells across its radius, an elder's a little more. */
    static final int SEARCH_RADIUS = 8;

    private static final ThreadLocal<LongBucket> NEARBY = ThreadLocal.withInitial(LongBucket::new);

    private HazeStepOut() { }

    static boolean stepOut(long unit, BattleControl sim) {
        NavigationGrid grid = sim.getGrid();
        int x = sim.world().cellX(unit), y = sim.world().cellY(unit);
        if (!grid.hasHaze(x, y) || !sim.world().hasMovement(unit) || !(sim.world().moveSpeed(unit) > 0f)) return false;
        if (inMelee(unit, sim)) return false;
        int[] path = sim.world().path(unit);
        int next = sim.world().pathIdx(unit);
        if (next < Paths.cellCount(path) && !grid.hasHaze(Paths.destX(path), Paths.destY(path))) {
            sim.advanceMovement(unit);
            return true;
        }
        int refuge = refuge(grid, sim.getOccupancyMap(), x, y);
        if (refuge < 0) return false;
        int[] out = GridPathfinder.findPath(grid, x, y, refuge % grid.getWidth(), refuge / grid.getWidth(),
                sim.getOccupancyMap());
        if (Paths.isEmpty(out)) return false;
        sim.setPath(unit, out);
        sim.advanceMovement(unit);
        return true;
    }

    /**
     * The nearest walkable, unhazed, unoccupied cell within {@link #SEARCH_RADIUS} of {@code (x, y)}, ring by ring
     * and nearest by distance within a ring, ties to the lower index; -1 for none.
     */
    static int refuge(NavigationGrid grid, byte[] occupancy, int x, int y) {
        for (int r = 1; r <= SEARCH_RADIUS; r++) {
            int best = -1;
            int bestD = Integer.MAX_VALUE;
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx, cy = y + dy;
                    if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy) || grid.hasHaze(cx, cy)) continue;
                    int idx = grid.index(cx, cy);
                    if (occupancy != null && (occupancy[idx] & 0xFF) != 0) continue;
                    int d = dx * dx + dy * dy;
                    if (d < bestD || (d == bestD && idx < best)) {
                        bestD = d;
                        best = idx;
                    }
                }
            }
            if (best >= 0) return best;
        }
        return -1;
    }

    private static boolean inMelee(long unit, BattleControl sim) {
        float ux = sim.world().x(unit), uy = sim.world().y(unit);
        Faction faction = sim.identity().faction(unit);
        LongBucket nearby = NEARBY.get();
        sim.getUnitIndex().gatherHostileCombatants(ux, uy, MELEE_REACH, faction, nearby);
        for (int i = 0, n = nearby.size; i < n; i++) {
            long other = nearby.ids[i];
            UnitType type = sim.identity().type(other);
            if (!type.isSwarm() || type == UnitType.SWARM_WORKER || type == UnitType.SWARM_BOMBARD) continue;
            float dx = sim.world().x(other) - ux, dy = sim.world().y(other) - uy;
            if (dx * dx + dy * dy <= MELEE_REACH * MELEE_REACH) return true;
        }
        return false;
    }
}
