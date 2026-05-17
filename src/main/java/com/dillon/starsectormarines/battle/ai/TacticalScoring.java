package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;

import java.util.ArrayList;
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
     * Four-arg line-of-sight predicate so callers can supply a non-standard
     * LoS rule (today: shuttle-mounted "air" turrets that ignore walls
     * within a few cells of their flying origin). The {@link Unit} overload
     * and the primitive overload both default to {@code grid::hasLineOfSight}.
     */
    @FunctionalInterface
    public interface LosTest {
        boolean visible(int fromX, int fromY, int toX, int toY);
    }

    /**
     * Picks the lowest-scored enemy where score = cell-distance + a per-engager
     * crowding penalty (heavier for squadmates than for general allies). Prefers
     * visible targets; falls back to nearest of any LOS so the unit pathfinds
     * toward them and visibility eventually opens.
     */
    public static Unit findBestTarget(Unit self, BattleSimulation sim) {
        return findBestTarget(self.cellX, self.cellY, self.faction, self.squadId, self, sim);
    }

    /**
     * Primitive-args overload — used by callers that aren't a {@link Unit}
     * themselves (today: shuttle-mounted turrets, which live as data on a
     * {@link com.dillon.starsectormarines.battle.air.Shuttle} rather than as
     * grid entities). The selection logic is identical; pass {@link Unit#NO_SQUAD}
     * for {@code squadId} and {@code null} for {@code excludeFromCrowding}
     * when the caller doesn't squad up and isn't itself in the unit list.
     */
    public static Unit findBestTarget(int selfCellX, int selfCellY, Faction selfFaction,
                                      int selfSquadId, Unit excludeFromCrowding, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        return findBestTarget(selfCellX, selfCellY, selfFaction, selfSquadId, excludeFromCrowding,
                grid::hasLineOfSight, sim);
    }

    /**
     * LoS-injectable overload — air turrets pass an "ignore close walls"
     * predicate so they can acquire targets through the building they're
     * hovering over. Logic otherwise identical to the standard-LoS overload.
     */
    public static Unit findBestTarget(int selfCellX, int selfCellY, Faction selfFaction,
                                      int selfSquadId, Unit excludeFromCrowding,
                                      LosTest los, BattleSimulation sim) {
        List<Unit> units = sim.getUnits();
        Unit bestVisible = null;
        float bestVisibleScore = Float.MAX_VALUE;
        Unit bestAny = null;
        float bestAnyDist = Float.MAX_VALUE;

        for (Unit other : units) {
            if (!other.isAlive()) continue;
            if (other.faction == selfFaction) continue;
            // Civilians and other non-combatants don't draw fire — they're
            // bystanders. A separate "rules of engagement" toggle could relax
            // this for pirate atrocity scenarios later.
            if (!other.type.combatant) continue;
            float d = cellDistance(selfCellX, selfCellY, other.cellX, other.cellY);
            if (d < bestAnyDist) {
                bestAnyDist = d;
                bestAny = other;
            }
            if (!los.visible(selfCellX, selfCellY, other.cellX, other.cellY)) continue;
            float crowding = scoreCrowding(selfFaction, selfSquadId, other, units, excludeFromCrowding);
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
    private static float scoreCrowding(Faction selfFaction, int selfSquadId, Unit target,
                                       List<Unit> units, Unit exclude) {
        float cost = 0f;
        for (Unit u : units) {
            if (u == exclude || !u.isAlive()) continue;
            if (u.faction != selfFaction) continue;
            if (u.target != target) continue;
            cost += TARGET_CROWDING_COST;
            if (selfSquadId != Unit.NO_SQUAD && u.squadId == selfSquadId) {
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

    /**
     * Constrained firing-position search — like {@link #findFiringPosition} but
     * rejects any candidate whose cell-distance from ({@code anchorX},
     * {@code anchorY}) exceeds {@code maxDistFromAnchor}. Used by
     * {@link GarrisonBehavior} to keep engaged defenders within a tight
     * radius of their tactical-node anchor: they'll peek around corners and
     * grab better cover, but won't chase marines off the wall.
     *
     * <p>Returns {@code null} (not the target's cell) when no candidate
     * satisfies range + LOS + anchor-radius. The caller treats null as
     * "hold position" rather than "advance toward the target."
     */
    public static int[] findFiringPositionWithin(Unit self, Unit target, BattleSimulation sim,
                                                  int anchorX, int anchorY, float maxDistFromAnchor) {
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

                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget > self.attackRange) continue;
                if (distFromTarget < FIRING_MIN_DISTANCE) continue;
                if (!grid.hasLineOfSight(cx, cy, tx, ty)) continue;
                if (cellDistance(anchorX, anchorY, cx, cy) > maxDistFromAnchor) continue;

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
        return best;
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
     * walkable, out-of-LOS spot, scored by
     * {@code distFromSelf + occupancyPenalty - coverBonus - zoneControlBonus}.
     *
     * <p>The top-scored cell only helps if the unit can actually walk to it —
     * and the wall that hides a cell from enemies is exactly the kind of wall
     * that can also seal it off from us. Without a reachability check the
     * picker happily returns sealed pockets and the unit freezes for the
     * entire fall-back duration waiting on an empty path. We filter using
     * {@link ZoneGraph#areConnected} (BFS over the portal graph, cheaper than
     * per-cell A*) and walk the sorted list until a reachable candidate
     * shows up.
     *
     * <p>If the top-scored cell is unreachable we first try its four cardinal
     * neighbors before falling through to the next-best candidate. The dud is
     * almost always shadowed by a wall, and the cardinal on our side of that
     * wall inherits the same cover while staying in our zone. Cardinals are
     * tried in order of distance from the average enemy cell (farthest first)
     * so the consolation pick doesn't accidentally march us into the firing
     * lane. Falls through to {@code self}'s current cell only if nothing
     * qualifies — caller treats that as "don't enter fall-back."
     */
    public static int[] findFallbackPosition(Unit self, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        ZoneGraph zones = sim.getZoneGraph();
        int sx = self.cellX;
        int sy = self.cellY;
        int selfZone = zones.zoneIdAt(sx, sy);
        int[] zoneControl = computeZoneControl(self, sim);

        List<float[]> candidates = new ArrayList<>();
        for (int dy = -FALLBACK_SCAN_RANGE; dy <= FALLBACK_SCAN_RANGE; dy++) {
            for (int dx = -FALLBACK_SCAN_RANGE; dx <= FALLBACK_SCAN_RANGE; dx++) {
                int cx = sx + dx;
                int cy = sy + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                if (!isHiddenFromAllEnemies(self, cx, cy, sim)) continue;

                int occupants = occupantsExcludingSelf(self, cx, cy, sim);
                int cover = grid.getCoverAt(cx, cy);
                int zoneId = zones.zoneIdAt(cx, cy);
                int control = (zoneId >= 0 && zoneId < zoneControl.length) ? zoneControl[zoneId] : 0;
                float distFromSelf = cellDistance(sx, sy, cx, cy);
                float score = distFromSelf
                        + FALLBACK_OCCUPANCY_COST * occupants
                        - FALLBACK_COVER_BONUS * cover
                        - FALLBACK_FRIENDLY_ZONE_BONUS * control;
                candidates.add(new float[]{score, cx, cy});
            }
        }
        candidates.sort((a, b) -> Float.compare(a[0], b[0]));

        int[] threatRef = averageEnemyCell(self, sim);
        for (float[] cand : candidates) {
            int cx = (int) cand[1];
            int cy = (int) cand[2];
            if (zones.areConnected(selfZone, zones.zoneIdAt(cx, cy))) {
                return new int[]{cx, cy};
            }
            int[] consolation = cardinalConsolation(grid, zones, selfZone, sx, sy, cx, cy, threatRef);
            if (consolation != null) return consolation;
        }
        return new int[]{sx, sy};
    }

    /**
     * Average alive-enemy cell from {@code self}'s perspective, or {@code
     * null} when there are no enemies. Used as the "don't march toward this"
     * reference for cardinal-neighbor ordering in fall-back consolation.
     */
    private static int[] averageEnemyCell(Unit self, BattleSimulation sim) {
        float sumX = 0f, sumY = 0f;
        int count = 0;
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || u.faction == self.faction) continue;
            if (!u.type.combatant) continue;
            sumX += u.cellX;
            sumY += u.cellY;
            count++;
        }
        if (count == 0) return null;
        return new int[]{Math.round(sumX / count), Math.round(sumY / count)};
    }

    /**
     * Picks a walkable cardinal neighbor of {@code (dudX, dudY)} in a zone
     * reachable from {@code selfZone}. Cardinals are tried farthest-from-
     * {@code threatRef} first so the consolation cell — taken when the
     * best-scored hide turns out to be sealed off — doesn't slide the unit
     * toward the threat. Returns {@code null} if no cardinal qualifies.
     */
    private static int[] cardinalConsolation(NavigationGrid grid, ZoneGraph zones,
                                             int selfZone, int sx, int sy,
                                             int dudX, int dudY, int[] threatRef) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] order = {0, 1, 2, 3};
        float[] threatDist = new float[4];
        for (int i = 0; i < 4; i++) {
            int nx = dudX + dirs[i][0];
            int ny = dudY + dirs[i][1];
            threatDist[i] = (threatRef == null)
                    ? 0f
                    : cellDistance(nx, ny, threatRef[0], threatRef[1]);
        }
        // Insertion sort by threatDist DESCENDING (farthest from threat first).
        for (int i = 1; i < 4; i++) {
            int slot = order[i];
            float d = threatDist[slot];
            int j = i;
            while (j > 0 && threatDist[order[j - 1]] < d) {
                order[j] = order[j - 1];
                j--;
            }
            order[j] = slot;
        }
        for (int oi : order) {
            int nx = dudX + dirs[oi][0];
            int ny = dudY + dirs[oi][1];
            if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny)) continue;
            if (nx == sx && ny == sy) continue;
            if (!zones.areConnected(selfZone, zones.zoneIdAt(nx, ny))) continue;
            return new int[]{nx, ny};
        }
        return null;
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
