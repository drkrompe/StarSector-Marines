package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.ArrayList;
import java.util.List;

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
 * @see <a href="file:roadmap/ai/stories/16-assault-command.md">Design doc</a>
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
            sectorAssignCount[sectorIdx]++;

            int targetZone = nearestDefenderZoneInSector(squad, sectorIdx, sim);
            if (targetZone < 0) {
                squad.assignedObjective = null;
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

        for (NavigationZone zone : graph.getZones()) {
            int[] cells = zone.getCellIndices();
            if (cells.length == 0) continue;
            float sumX = 0f, sumY = 0f;
            for (int cellIdx : cells) {
                sumX += (cellIdx % gridW);
                sumY += (cellIdx / gridW);
            }
            float cx = sumX / cells.length;
            float cy = sumY / cells.length;
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

        sectorCentroidX = new float[sectorCount];
        sectorCentroidY = new float[sectorCount];
        for (int s = 0; s < sectorCount; s++) {
            List<Integer> zones = sectorZones.get(s);
            if (zones.isEmpty()) continue;
            float sx = 0f, sy = 0f;
            for (int zid : zones) {
                sx += zoneCentroidX[zid];
                sy += zoneCentroidY[zid];
            }
            sectorCentroidX[s] = sx / zones.size();
            sectorCentroidY[s] = sy / zones.size();
        }
    }

    private void computeActiveSectors(BattleView sim, boolean[] active, int[] defenderZoneCount) {
        int sectorCount = sectorCols * sectorRows;
        for (int s = 0; s < sectorCount; s++) {
            int count = 0;
            for (int zoneId : sectorZones.get(s)) {
                if (!ZoneQueries.zoneClear(zoneId, Faction.DEFENDER, sim)) {
                    count++;
                }
            }
            defenderZoneCount[s] = count;
            active[s] = count > 0;
        }
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
