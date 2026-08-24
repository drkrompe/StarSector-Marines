package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.infantry.PatrolMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Marine-side strategic commander for ASSAULT — the search-and-destroy
 * pattern (sweep the map, eliminate all defenders). Partitions the map
 * into a rectangular grid of sectors at first tick, then per slow tick
 * assigns each marine squad to the nearest active sector and picks the
 * nearest defender-occupied zone within it.
 *
 * <p>Distinct partition strategy from {@link ConquestCommand}'s lateral
 * strips (axis-aligned, sticky) and {@link SabotageCommand}'s objective
 * clusters (centered on charge sites). ASSAULT has no traversal axis and
 * no named targets — the partition is purely spatial.
 *
 * <p>Non-sticky assignment: squads are re-evaluated each slow tick so
 * they naturally converge on remaining hotspots as sectors clear. When
 * squads outnumber active sectors, surplus squads double up on the
 * busiest sector — implicit convergence without an explicit mechanism.
 *
 * @see {@code ai-nouns.md}
 */
public final class AssaultCommand implements MissionCommand {

    private static final int MIN_SECTOR_DIM = 2;
    private static final int MAX_SECTOR_DIM = 3;
    private static final int TARGET_SECTOR_WIDTH = 30;
    private static final int TARGET_SECTOR_HEIGHT = 15;

    private boolean initialized = false;
    private int sectorCols;
    private int sectorRows;
    private List<List<Integer>> sectorZones;
    private float[] zoneCentroidX;
    private float[] zoneCentroidY;
    private float[] sectorCentroidX;
    private float[] sectorCentroidY;
    /** Deterministic serpentine search cells for each rectangular sector. */
    private List<List<int[]>> sectorSweepWaypoints;
    /** Commander-owned progress so tactical replans do not restart a search route. */
    private final Map<Integer, Integer> sweepSectorBySquad = new HashMap<>();
    private final Map<Integer, Integer> sweepCursorBySquad = new HashMap<>();
    /**
     * Zone id of the open exterior — the largest zone by cell count, cached at
     * init. Never handed out as a {@code CLEAR_ZONE} target: the exterior
     * flood spans the map, so a squad ordered to clear it chases individual
     * defenders forever. Outdoor defenders instead activate their rectangular
     * sector and receive a {@link AssignmentKind#SWEEP_SECTOR} route. Keyed
     * on largest-by-cells rather than id 0
     * because the flood-fill ids zones by scan order. Only set when the largest
     * zone <em>dominates</em> (≥ {@link #EXTERIOR_DOMINANCE_RATIO}× the
     * second-largest), so a map of comparably-sized rooms excludes nothing.
     * {@code -1} until init / when nothing dominates.
     */
    private int exteriorZoneId = -1;
    /** The largest zone is the open exterior only when at least this many times bigger than the next-largest. */
    private static final float EXTERIOR_DOMINANCE_RATIO = 2.0f;

    @Override
    public Faction faction() {
        return Faction.MARINE;
    }

    @Override
    public void tick(BattleView sim) {
        if (!initialized) {
            initializeSectors(sim);
            initialized = true;
        }

        int sectorCount = sectorCols * sectorRows;
        boolean[] active = new boolean[sectorCount];
        int[] defenderZoneCount = new int[sectorCount];
        computeActiveSectors(sim, active, defenderZoneCount);

        int[] sectorAssignCount = new int[sectorCount];

        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE) continue;
            if (squad.aliveMembers <= 0) continue;

            int sectorIdx = pickSector(squad, active, defenderZoneCount, sectorAssignCount);
            if (sectorIdx < 0) {
                squad.assignedObjective = null;
                continue;
            }
            int sectorLane = sectorAssignCount[sectorIdx]++;

            int targetZone = nearestDefenderZoneInSector(squad, sectorIdx, sim);
            if (targetZone < 0) {
                int[] target = sweepTarget(squad, sectorIdx, sectorLane);
                if (target == null) {
                    squad.assignedObjective = null;
                    continue;
                }
                ObjectiveAssignment cur = squad.assignedObjective;
                if (cur == null
                        || cur.kind() != AssignmentKind.SWEEP_SECTOR
                        || cur.targetCellX() != target[0]
                        || cur.targetCellY() != target[1]) {
                    squad.assignedObjective = ObjectiveAssignment.sweepSector(
                            squad.id, target[0], target[1]);
                }
                continue;
            }

            ObjectiveAssignment cur = squad.assignedObjective;
            if (cur == null
                    || cur.kind() != AssignmentKind.CLEAR_ZONE
                    || cur.targetZoneId() != targetZone) {
                squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, targetZone);
            }
        }
    }

    private void initializeSectors(BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        ZoneGraph graph = sim.getZoneGraph();
        int gridW = grid.getWidth();
        int gridH = grid.getHeight();

        sectorCols = Math.max(MIN_SECTOR_DIM, Math.min(MAX_SECTOR_DIM, gridW / TARGET_SECTOR_WIDTH));
        sectorRows = Math.max(MIN_SECTOR_DIM, Math.min(MAX_SECTOR_DIM, gridH / TARGET_SECTOR_HEIGHT));
        int sectorCount = sectorCols * sectorRows;

        sectorZones = new ArrayList<>(sectorCount);
        for (int i = 0; i < sectorCount; i++) sectorZones.add(new ArrayList<>());

        int zoneCount = graph.getZones().size();
        zoneCentroidX = new float[zoneCount];
        zoneCentroidY = new float[zoneCount];

        int largestCells = -1, secondCells = -1, largestZone = -1;
        for (NavigationZone zone : graph.getZones()) {
            int[] cells = zone.getCellIndices();
            if (cells.length == 0) continue;
            if (cells.length > largestCells) {
                secondCells = largestCells;
                largestCells = cells.length;
                largestZone = zone.getZoneId();
            } else if (cells.length > secondCells) {
                secondCells = cells.length;
            }
            float sumX = 0f, sumY = 0f;
            for (int cellIdx : cells) {
                sumX += (cellIdx % gridW);
                sumY += (cellIdx / gridW);
            }
            // Center-based (cell centers averaged), so comparisons against
            // squad centroids — true-position means — are convention-matched.
            float cx = sumX / cells.length + 0.5f;
            float cy = sumY / cells.length + 0.5f;
            int id = zone.getZoneId();
            if (id >= 0 && id < zoneCount) {
                zoneCentroidX[id] = cx;
                zoneCentroidY[id] = cy;
            }

            int col = Math.min((int) (cx / gridW * sectorCols), sectorCols - 1);
            int row = Math.min((int) (cy / gridH * sectorRows), sectorRows - 1);
            if (col < 0) col = 0;
            if (row < 0) row = 0;
            sectorZones.get(row * sectorCols + col).add(id);
        }

        if (largestZone >= 0
                && (secondCells <= 0 || largestCells >= EXTERIOR_DOMINANCE_RATIO * secondCells)) {
            exteriorZoneId = largestZone;
        }

        sectorCentroidX = new float[sectorCount];
        sectorCentroidY = new float[sectorCount];
        for (int row = 0; row < sectorRows; row++) {
            for (int col = 0; col < sectorCols; col++) {
                int sector = row * sectorCols + col;
                sectorCentroidX[sector] = (col + 0.5f) * gridW / sectorCols;
                sectorCentroidY[sector] = (row + 0.5f) * gridH / sectorRows;
            }
        }
        sectorSweepWaypoints = buildSweepWaypoints(grid);
    }

    private void computeActiveSectors(BattleView sim, boolean[] active, int[] defenderZoneCount) {
        NavigationGrid grid = sim.getGrid();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER
                    || !sim.identity().type(unit).combatant) continue;
            int sector = sectorForCell(sim.world().cellX(unit),
                    sim.world().cellY(unit), grid);
            if (sector < 0) continue;
            active[sector] = true;
            defenderZoneCount[sector]++;
        }
    }

    private int sectorForCell(int x, int y, NavigationGrid grid) {
        if (!grid.inBounds(x, y)) return -1;
        int col = Math.min(x * sectorCols / grid.getWidth(), sectorCols - 1);
        int row = Math.min(y * sectorRows / grid.getHeight(), sectorRows - 1);
        return row * sectorCols + col;
    }

    /**
     * Pick the best sector for this squad. Nearest active sector by centroid
     * distance, with a bias toward the squad's current sector to prevent
     * flip-flop churn. When all squads have been assigned and surplus squads
     * remain, they double up on the sector with the most defender-occupied
     * zones.
     */
    private int pickSector(Squad squad, boolean[] active, int[] defenderZoneCount, int[] assignCount) {
        int sectorCount = sectorCols * sectorRows;

        // Identify current sector (the one the squad's existing assignment targets)
        int currentSector = -1;
        ObjectiveAssignment cur = squad.assignedObjective;
        if (cur != null && cur.kind() == AssignmentKind.CLEAR_ZONE && cur.targetZoneId() >= 0) {
            currentSector = sectorForZone(cur.targetZoneId());
        } else if (cur != null && cur.kind() == AssignmentKind.SWEEP_SECTOR) {
            currentSector = sweepSectorBySquad.getOrDefault(squad.id, -1);
        }

        int bestSector = -1;
        float bestScore = Float.MAX_VALUE;

        for (int s = 0; s < sectorCount; s++) {
            if (!active[s]) continue;
            float dx = squad.centroidX - sectorCentroidX[s];
            float dy = squad.centroidY - sectorCentroidY[s];
            float distSq = dx * dx + dy * dy;
            // Bias toward current sector to reduce churn
            if (s == currentSector) distSq *= 0.7f;
            // Penalize sectors that already have a squad assigned (spread first)
            float loadPenalty = assignCount[s] * 2000f;
            float score = distSq + loadPenalty;
            if (score < bestScore) {
                bestScore = score;
                bestSector = s;
            }
        }
        return bestSector;
    }

    private int sectorForZone(int zoneId) {
        int sectorCount = sectorCols * sectorRows;
        for (int s = 0; s < sectorCount; s++) {
            if (sectorZones.get(s).contains(zoneId)) return s;
        }
        return -1;
    }

    private int nearestDefenderZoneInSector(Squad squad, int sectorIdx, BattleView sim) {
        if (sectorIdx < 0 || sectorIdx >= sectorZones.size()) return -1;
        int bestZone = -1;
        float bestDistSq = Float.MAX_VALUE;
        for (int zoneId : sectorZones.get(sectorIdx)) {
            if (zoneId == exteriorZoneId) continue;
            if (ZoneQueries.zoneClear(zoneId, Faction.DEFENDER, sim)) continue;
            float dx = zoneCentroidX[zoneId] - squad.centroidX;
            float dy = zoneCentroidY[zoneId] - squad.centroidY;
            float d = dx * dx + dy * dy;
            if (d < bestDistSq) {
                bestDistSq = d;
                bestZone = zoneId;
            }
        }
        return bestZone;
    }

    /**
     * Returns the current search cell, advancing around the sector's
     * serpentine route after the squad centroid reaches a waypoint. The
     * initial lane offset prevents multiple squads in the last live sector
     * from tracing the same route shoulder-to-shoulder.
     */
    private int[] sweepTarget(Squad squad, int sectorIdx, int sectorLane) {
        if (sectorSweepWaypoints == null || sectorIdx < 0
                || sectorIdx >= sectorSweepWaypoints.size()) return null;
        List<int[]> waypoints = sectorSweepWaypoints.get(sectorIdx);
        if (waypoints.isEmpty()) return null;

        Integer oldSector = sweepSectorBySquad.get(squad.id);
        int cursor;
        if (oldSector == null || oldSector != sectorIdx) {
            cursor = (nearestWaypoint(squad, waypoints) + sectorLane) % waypoints.size();
            sweepSectorBySquad.put(squad.id, sectorIdx);
            sweepCursorBySquad.put(squad.id, cursor);
        } else {
            cursor = sweepCursorBySquad.getOrDefault(squad.id, 0) % waypoints.size();
            int[] current = waypoints.get(cursor);
            float dx = squad.centroidX - (current[0] + 0.5f);
            float dy = squad.centroidY - (current[1] + 0.5f);
            if (dx * dx + dy * dy
                    <= PatrolMotion.ARRIVAL_RADIUS * PatrolMotion.ARRIVAL_RADIUS) {
                cursor = (cursor + 1) % waypoints.size();
                sweepCursorBySquad.put(squad.id, cursor);
            }
        }
        return waypoints.get(cursor);
    }

    private static int nearestWaypoint(Squad squad, List<int[]> waypoints) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < waypoints.size(); i++) {
            int[] waypoint = waypoints.get(i);
            float dx = squad.centroidX - (waypoint[0] + 0.5f);
            float dy = squad.centroidY - (waypoint[1] + 0.5f);
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private List<List<int[]>> buildSweepWaypoints(NavigationGrid grid) {
        List<List<int[]>> result = new ArrayList<>(sectorCols * sectorRows);
        for (int row = 0; row < sectorRows; row++) {
            for (int col = 0; col < sectorCols; col++) {
                int minX = col * grid.getWidth() / sectorCols;
                int maxX = (col + 1) * grid.getWidth() / sectorCols - 1;
                int minY = row * grid.getHeight() / sectorRows;
                int maxY = (row + 1) * grid.getHeight() / sectorRows - 1;
                List<int[]> waypoints = new ArrayList<>();
                int[][] samples = {
                        {1, 4}, {3, 4}, {5, 4},
                        {5, 6}, {3, 6}, {1, 6}
                };
                for (int[] sample : samples) {
                    int x = minX + Math.max(0,
                            Math.round((maxX - minX) * sample[0] / 6f));
                    int y = minY + Math.max(0,
                            Math.round((maxY - minY) * sample[1] / 10f));
                    int[] waypoint = nearestWalkable(grid, x, y,
                            minX, maxX, minY, maxY);
                    if (waypoint != null && !containsCell(waypoints, waypoint)) {
                        waypoints.add(waypoint);
                    }
                }
                result.add(waypoints);
            }
        }
        return result;
    }

    private static int[] nearestWalkable(NavigationGrid grid, int targetX, int targetY,
                                         int minX, int maxX, int minY, int maxY) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!grid.isWalkable(x, y)) continue;
                int dx = x - targetX;
                int dy = y - targetY;
                int distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    private static boolean containsCell(List<int[]> cells, int[] candidate) {
        for (int[] cell : cells) {
            if (cell[0] == candidate[0] && cell[1] == candidate[1]) return true;
        }
        return false;
    }

    // ---- Test/debug accessors ----

    public int sectorCount() {
        return sectorCols * sectorRows;
    }

    public int sectorCols() {
        return sectorCols;
    }

    public int sectorRows() {
        return sectorRows;
    }

    public List<Integer> zonesInSector(int sectorIdx) {
        if (sectorZones == null || sectorIdx < 0 || sectorIdx >= sectorZones.size()) {
            return List.of();
        }
        return List.copyOf(sectorZones.get(sectorIdx));
    }
}
