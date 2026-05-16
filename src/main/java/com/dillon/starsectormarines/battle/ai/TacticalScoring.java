package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;

import java.util.List;

/**
 * Pure scoring helpers used by behaviors to pick targets and positions.
 * Stateless; each call takes the sim plus the acting unit and computes a
 * fresh answer. Pulled out of {@link BattleSimulation} so behavior code
 * stays thin and the math is reusable / testable in isolation.
 *
 * <p>Conventions:
 * <ul>
 *   <li><b>Cost-based</b> — lower score is better. Penalties add; bonuses subtract.</li>
 *   <li><b>Squad-aware</b> — {@link #findBestTarget} adds a heavier crowding
 *       penalty for squadmates already aiming at a target than for arbitrary
 *       allies, so a 4-man squad naturally spreads its fire across the front
 *       rather than collapsing onto a single enemy.</li>
 *   <li><b>Cover-aware</b> — firing-position and fall-back scoring read
 *       per-cell cover from the grid, biasing units toward wall-adjacent
 *       cells they can peek from.</li>
 * </ul>
 */
public final class TacticalScoring {

    /** Per-engaging-ally penalty added to target selection — pushes the squad to spread fire instead of dogpiling. */
    public static final float TARGET_CROWDING_COST = 6f;
    /** Extra penalty when the engager is a squadmate. Real fireteams cover sectors, not the same enemy. */
    public static final float TARGET_SQUADMATE_EXTRA_COST = 6f;

    /** Per-ally-on-cell penalty added when picking a firing position around a target — pushes units into a spread ring. */
    public static final float FIRING_OCCUPANCY_COST = 4f;
    /** Minimum cell-distance from target when picking a firing position. Avoids picking the target's own cell. */
    public static final float FIRING_MIN_DISTANCE = 0.7f;
    /** Per-cover-level bonus subtracted from firing-position score. Pushes units to peek from corners and wall edges. */
    public static final float FIRING_COVER_BONUS = 3f;

    /** Cell radius searched for a fall-back position around the hit unit. */
    public static final int   FALLBACK_SCAN_RANGE = 8;
    /** Per-ally-on-cell penalty in fall-back scoring. */
    public static final float FALLBACK_OCCUPANCY_COST = 4f;
    /** Per-cover-level bonus in fall-back scoring. */
    public static final float FALLBACK_COVER_BONUS    = 2f;
    /** Bonus subtracted from fall-back score per net ally in the candidate cell's zone. Pulls retreating units toward where their squad lives rather than the nearest blind corner. */
    public static final float FALLBACK_FRIENDLY_ZONE_BONUS = 1.5f;

    private TacticalScoring() {}

    /**
     * Picks the lowest-scored enemy where score = cell-distance + a per-engager
     * crowding penalty (heavier for squadmates than for general allies). Prefers
     * visible targets; falls back to nearest of any LOS so the unit pathfinds
     * toward them and visibility eventually opens.
     */
    public static Unit findBestTarget(Unit self, BattleSimulation sim) {
        List<Unit> units = sim.getUnits();
        NavigationGrid grid = sim.getGrid();
        Unit bestVisible = null;
        float bestVisibleScore = Float.MAX_VALUE;
        Unit bestAny = null;
        float bestAnyDist = Float.MAX_VALUE;

        for (Unit other : units) {
            if (!other.isAlive()) continue;
            if (other.faction == self.faction) continue;
            float d = cellDistance(self.cellX, self.cellY, other.cellX, other.cellY);
            if (d < bestAnyDist) {
                bestAnyDist = d;
                bestAny = other;
            }
            if (!grid.hasLineOfSight(self.cellX, self.cellY, other.cellX, other.cellY)) continue;
            float crowding = scoreCrowding(self, other, units);
            float score = d + crowding;
            if (score < bestVisibleScore) {
                bestVisibleScore = score;
                bestVisible = other;
            }
        }
        return bestVisible != null ? bestVisible : bestAny;
    }

    /**
     * Adds {@link #TARGET_CROWDING_COST} for every ally targeting the same
     * enemy, plus an additional {@link #TARGET_SQUADMATE_EXTRA_COST} when the
     * ally is a squadmate. Squadmate weight is higher so fireteams spread
     * naturally across the visible front, while cross-squad pile-ups are only
     * mildly discouraged.
     */
    private static float scoreCrowding(Unit self, Unit target, List<Unit> units) {
        float cost = 0f;
        for (Unit u : units) {
            if (u == self || !u.isAlive()) continue;
            if (u.faction != self.faction) continue;
            if (u.target != target) continue;
            cost += TARGET_CROWDING_COST;
            if (self.squadId != Unit.NO_SQUAD && u.squadId == self.squadId) {
                cost += TARGET_SQUADMATE_EXTRA_COST;
            }
        }
        return cost;
    }

    /**
     * Picks a walkable cell at attack range from the target, minimizing
     * {@code distFromSelf + occupancy_penalty - cover_bonus}. Candidates must
     * have LOS to the target — a cell on the far side of a wall is useless
     * even at range. Returns the target's own cell as a fallback if no
     * candidate qualifies.
     */
    public static int[] findFiringPosition(Unit self, Unit target, BattleSimulation sim) {
        return findFiringPosition(self, target, sim, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public static int[] findFiringPosition(Unit self, Unit target, BattleSimulation sim, int rejectX, int rejectY) {
        NavigationGrid grid = sim.getGrid();
        int range = Math.max(1, (int) Math.floor(self.attackRange));
        int tx = target.cellX;
        int ty = target.cellY;

        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                int cx = tx + dx;
                int cy = ty + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                if (cx == rejectX && cy == rejectY) continue;

                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget > self.attackRange) continue;
                if (distFromTarget < FIRING_MIN_DISTANCE) continue;
                if (!grid.hasLineOfSight(cx, cy, tx, ty)) continue;

                int occupants = occupantsExcludingSelf(self, cx, cy, sim);
                int cover = grid.getCoverAt(cx, cy);
                float distFromSelf = cellDistance(self.cellX, self.cellY, cx, cy);
                float score = distFromSelf
                        + FIRING_OCCUPANCY_COST * occupants
                        - FIRING_COVER_BONUS * cover;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best != null ? best : new int[]{tx, ty};
    }

    /**
     * Scans cells within {@link #FALLBACK_SCAN_RANGE} of {@code self} for a
     * walkable spot with no LOS to any alive enemy. Scores by
     * {@code distFromSelf + occupancyPenalty - coverBonus}. Falls through to
     * {@code self}'s current cell if nothing qualifies.
     */
    public static int[] findFallbackPosition(Unit self, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        int sx = self.cellX;
        int sy = self.cellY;
        int[] zoneControl = computeZoneControl(self, sim);
        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int dy = -FALLBACK_SCAN_RANGE; dy <= FALLBACK_SCAN_RANGE; dy++) {
            for (int dx = -FALLBACK_SCAN_RANGE; dx <= FALLBACK_SCAN_RANGE; dx++) {
                int cx = sx + dx;
                int cy = sy + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                if (!isHiddenFromAllEnemies(self, cx, cy, sim)) continue;

                int occupants = occupantsExcludingSelf(self, cx, cy, sim);
                int cover = grid.getCoverAt(cx, cy);
                int zoneId = sim.getZoneGraph().zoneIdAt(cx, cy);
                int control = (zoneId >= 0 && zoneId < zoneControl.length) ? zoneControl[zoneId] : 0;
                float distFromSelf = cellDistance(sx, sy, cx, cy);
                float score = distFromSelf
                        + FALLBACK_OCCUPANCY_COST * occupants
                        - FALLBACK_COVER_BONUS * cover
                        - FALLBACK_FRIENDLY_ZONE_BONUS * control;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best != null ? best : new int[]{sx, sy};
    }

    /**
     * Per-zone allies-minus-enemies from {@code self}'s perspective.
     * Indexed by zone id; positive = friendly-controlled, negative = hostile.
     * Computed once per {@link #findFallbackPosition} call so a 17×17 candidate
     * scan does at most O(zones + units) work instead of re-scanning units
     * per cell.
     */
    private static int[] computeZoneControl(Unit self, BattleSimulation sim) {
        ZoneGraph zones = sim.getZoneGraph();
        int[] control = new int[zones.getZones().size()];
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive()) continue;
            int zid = zones.zoneIdAt(u.cellX, u.cellY);
            if (zid < 0 || zid >= control.length) continue;
            control[zid] += (u.faction == self.faction) ? 1 : -1;
        }
        return control;
    }

    public static boolean isHiddenFromAllEnemies(Unit self, int cx, int cy, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        for (Unit other : sim.getUnits()) {
            if (!other.isAlive()) continue;
            if (other.faction == self.faction) continue;
            if (grid.hasLineOfSight(cx, cy, other.cellX, other.cellY)) return false;
        }
        return true;
    }

    /**
     * Occupancy count at cell (cx, cy), excluding self's own contributions
     * (current cell + path destination). Used so a unit doesn't penalize
     * itself when scoring its own current/intended position.
     */
    public static int occupantsExcludingSelf(Unit self, int cx, int cy, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        if (!grid.inBounds(cx, cy)) return 0;
        int n = sim.getOccupancyMap()[cy * grid.getWidth() + cx] & 0xFF;
        if (cx == self.cellX && cy == self.cellY) n--;
        int[] selfDest = pathDestination(self);
        if (selfDest != null && selfDest[0] == cx && selfDest[1] == cy
                && (selfDest[0] != self.cellX || selfDest[1] != self.cellY)) {
            n--;
        }
        return Math.max(0, n);
    }

    private static int[] pathDestination(Unit u) {
        return u.path.isEmpty() ? null : u.path.get(u.path.size() - 1);
    }

    public static float cellDistance(int x0, int y0, int x1, int y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
