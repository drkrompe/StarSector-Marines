package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.objective.EliminateFactionObjective;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Builds a battle scenario for the auto-battler. v3: marines arrive via
 * scheduled shuttle drops rather than pre-spawning. {@link UrbanMapGenerator}
 * carves the city; we pick spread-out landing zones in the marine quadrant,
 * stagger the drops, and let {@link BattleSimulation} run the state machine.
 * Defenders still pre-spawn (lore-correct: they're already on the ground).
 */
public final class BattleSetup {

    /** Default battle grid size (cells). Was 24x16 during the MVP loop; widened for urban combat. */
    public static final int GRID_W = 96;
    public static final int GRID_H = 48;

    private static final int DEFENDER_COUNT = 12;

    /** Three drops × 4 marines/shuttle keeps total marine count at 12 — matches pre-shuttle balance. */
    private static final int SHUTTLE_COUNT = 3;
    /** Sim-seconds between successive shuttle launches. Spaces out drops so the LZs aren't all active at once. */
    private static final float SHUTTLE_DROP_STAGGER_SEC = 1.5f;
    /** Minimum cell-distance between landing zones — avoids stacking all shuttles on the spawn anchor. */
    private static final int LZ_MIN_SEPARATION = 8;
    /** Entry/exit Y offset above the grid (in cells). Long enough that shuttles are visible during their descent. */
    private static final float SHUTTLE_OFFMAP_Y = 8f;

    /** BFS radius around the defender anchor we scan for candidate spawn cells. Larger than {@link #DEFENDER_COUNT} so we have a pool to cherry-pick high-cover cells from. */
    private static final int DEFENDER_SPAWN_SCAN_RADIUS = 14;

    /** SABOTAGE: number of charge sites to plant. One per shuttle = one planter per drop. */
    private static final int SABOTAGE_CHARGE_SITES = 3;
    /** SABOTAGE: sim-seconds a planter must dwell on a charge site to complete the plant. */
    private static final float SABOTAGE_PLANT_DURATION = 5.0f;

    private BattleSetup() {}

    public static BattleSimulation createPlaceholder() {
        return createPlaceholder(System.currentTimeMillis());
    }

    public static BattleSimulation createSabotage() {
        return createSabotage(System.currentTimeMillis());
    }

    /**
     * SABOTAGE variant: marines must plant charges on {@link #SABOTAGE_CHARGE_SITES}
     * target structures while defenders try to keep them off the sites. One
     * marine per shuttle drops in with the PLANTER role pre-assigned; the rest
     * deboard as combatants and provide cover fire.
     *
     * <p>Marine win: every {@link ChargeSiteObjective} completes AND at least
     * one marine is alive when the last charge sets. Defender win: kill every
     * marine before the charges go off.
     */
    public static BattleSimulation createSabotage(long seed) {
        UrbanMapGenerator.Result map = UrbanMapGenerator.generate(GRID_W, GRID_H, seed);
        BattleSimulation sim = new BattleSimulation(map.grid);

        // Pick charge sites: prefer high-value POIs (lab/comms/depot) in the
        // defender half of the map. Fall back to any POI if not enough qualify.
        List<PointOfInterest> sites = pickChargeSites(map.pointsOfInterest, GRID_W / 2, SABOTAGE_CHARGE_SITES);
        List<ChargeSiteObjective> objectives = new ArrayList<>(sites.size());
        for (PointOfInterest poi : sites) {
            ChargeSiteObjective obj = new ChargeSiteObjective(
                    poi.anchorCellX, poi.anchorCellY,
                    SABOTAGE_PLANT_DURATION,
                    "Plant charge: " + poi.kind.name().toLowerCase());
            objectives.add(obj);
            sim.addObjective(obj);
        }
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));

        // Marines: same shuttle drops as ASSAULT, but each shuttle's first
        // deboard is a PLANTER pointed at one of the charge sites. Pair them
        // one-to-one if counts match; otherwise wrap around.
        List<int[]> lzCells = pickLandingZones(map.grid, map.marineSpawnX, map.marineSpawnY, SHUTTLE_COUNT);
        float topEdgeY = GRID_H;
        for (int i = 0; i < lzCells.size(); i++) {
            int[] lz = lzCells.get(i);
            float lzCenterX = lz[0] + 0.5f;
            float lzCenterY = lz[1] + 0.5f;
            float entryX = lzCenterX;
            float entryY = topEdgeY + SHUTTLE_OFFMAP_Y;
            float exitX  = lzCenterX;
            float exitY  = topEdgeY + SHUTTLE_OFFMAP_Y + 4f;
            Shuttle shuttle = new Shuttle(
                    ShuttleType.BASIC_SHUTTLE, Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entryX, entryY,
                    exitX, exitY,
                    i * SHUTTLE_DROP_STAGGER_SEC);
            shuttle.marineLoadout = buildSabotageLoadout(shuttle.type.capacity, objectives, i);
            sim.addShuttle(shuttle);
        }

        List<int[]> defenderCells = pickDefensiveCluster(map.grid, map.defenderSpawnX, map.defenderSpawnY, DEFENDER_COUNT);
        for (int i = 0; i < defenderCells.size(); i++) {
            int[] p = defenderCells.get(i);
            sim.addUnit(new Unit("d" + i, Faction.DEFENDER, p[0], p[1]));
        }
        return sim;
    }

    /**
     * Filters POIs to the defender half (x >= halfX), prefers lab/comms/depot
     * kinds over residential, and returns up to {@code count} of them spaced
     * apart by at least {@link #LZ_MIN_SEPARATION}. Falls back to any POI in
     * defender territory if not enough valuable ones exist.
     */
    private static List<PointOfInterest> pickChargeSites(List<PointOfInterest> all, int halfX, int count) {
        List<PointOfInterest> highValue = new ArrayList<>();
        List<PointOfInterest> anyDefender = new ArrayList<>();
        for (PointOfInterest poi : all) {
            if (poi.centerX() < halfX) continue;
            anyDefender.add(poi);
            if (poi.kind != PointOfInterest.Kind.RESIDENTIAL) highValue.add(poi);
        }
        List<PointOfInterest> picked = new ArrayList<>();
        int minSepSq = LZ_MIN_SEPARATION * LZ_MIN_SEPARATION;
        for (List<PointOfInterest> pool : List.of(highValue, anyDefender)) {
            for (PointOfInterest poi : pool) {
                if (picked.size() >= count) break;
                boolean farEnough = true;
                for (PointOfInterest prev : picked) {
                    int dx = prev.anchorCellX - poi.anchorCellX;
                    int dy = prev.anchorCellY - poi.anchorCellY;
                    if (dx * dx + dy * dy < minSepSq) { farEnough = false; break; }
                }
                if (farEnough && !picked.contains(poi)) picked.add(poi);
            }
            if (picked.size() >= count) break;
        }
        return picked;
    }

    /**
     * Slot 0 of each shuttle gets a PLANTER assigned to a charge site (paired
     * by shuttle index, wrapping around if shuttle count and site count differ).
     * Remaining slots are plain combatants.
     */
    private static MarineLoadout[] buildSabotageLoadout(int capacity, List<ChargeSiteObjective> sites, int shuttleIndex) {
        MarineLoadout[] roster = new MarineLoadout[capacity];
        for (int i = 0; i < capacity; i++) roster[i] = MarineLoadout.COMBATANT;
        if (!sites.isEmpty()) {
            ChargeSiteObjective site = sites.get(shuttleIndex % sites.size());
            roster[0] = new MarineLoadout(UnitRole.PLANTER, site);
        }
        return roster;
    }

    public static BattleSimulation createPlaceholder(long seed) {
        UrbanMapGenerator.Result map = UrbanMapGenerator.generate(GRID_W, GRID_H, seed);
        BattleSimulation sim = new BattleSimulation(map.grid);

        // Default ASSAULT objectives — eliminate the other side. Mission-specific
        // setups (sabotage, raid, extraction) will swap or add to this pair.
        sim.addObjective(new EliminateFactionObjective(Faction.MARINE, Faction.DEFENDER));
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));

        // Marines: schedule SHUTTLE_COUNT staggered drops, each at its own LZ.
        // Marines spawn when each shuttle reaches LANDED and the deboard timer fires.
        List<int[]> lzCells = pickLandingZones(map.grid, map.marineSpawnX, map.marineSpawnY, SHUTTLE_COUNT);
        float topEdgeY = GRID_H;
        for (int i = 0; i < lzCells.size(); i++) {
            int[] lz = lzCells.get(i);
            float lzCenterX = lz[0] + 0.5f;
            float lzCenterY = lz[1] + 0.5f;
            // Entry directly above the LZ, off the top of the grid. Exit a bit further off to give
            // the departing shuttle a moment of visible climb before it disappears.
            float entryX = lzCenterX;
            float entryY = topEdgeY + SHUTTLE_OFFMAP_Y;
            float exitX  = lzCenterX;
            float exitY  = topEdgeY + SHUTTLE_OFFMAP_Y + 4f;
            sim.addShuttle(new Shuttle(
                    ShuttleType.BASIC_SHUTTLE, Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entryX, entryY,
                    exitX, exitY,
                    i * SHUTTLE_DROP_STAGGER_SEC));
        }

        // Defenders pre-spawn around their anchor, biased to high-cover cells —
        // they prepared the position. Falls back to plain BFS order if the
        // local area is open (e.g., a plaza right at the anchor).
        List<int[]> defenderCells = pickDefensiveCluster(map.grid, map.defenderSpawnX, map.defenderSpawnY, DEFENDER_COUNT);
        for (int i = 0; i < defenderCells.size(); i++) {
            int[] p = defenderCells.get(i);
            sim.addUnit(new Unit("d" + i, Faction.DEFENDER, p[0], p[1]));
        }
        return sim;
    }

    /**
     * BFS from the marine anchor; keeps the first {@code count} walkable cells
     * that are each at least {@link #LZ_MIN_SEPARATION} from every previously
     * picked LZ. Spreads drops across the marine quadrant instead of stacking
     * them on the anchor. Falls back to the anchor itself if not enough spread
     * cells exist (tight map) — better one stacked LZ than zero shuttles.
     */
    private static List<int[]> pickLandingZones(NavigationGrid grid, int anchorX, int anchorY, int count) {
        List<int[]> picked = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{anchorX, anchorY});
        seen.add(key(anchorX, anchorY));
        int minSepSq = LZ_MIN_SEPARATION * LZ_MIN_SEPARATION;
        while (!q.isEmpty() && picked.size() < count) {
            int[] p = q.poll();
            if (grid.isWalkable(p[0], p[1])) {
                boolean farEnough = true;
                for (int[] prev : picked) {
                    int dx = prev[0] - p[0];
                    int dy = prev[1] - p[1];
                    if (dx * dx + dy * dy < minSepSq) {
                        farEnough = false;
                        break;
                    }
                }
                if (farEnough) picked.add(new int[]{p[0], p[1]});
            }
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny});
            }
        }
        while (picked.size() < count) picked.add(new int[]{anchorX, anchorY});
        return picked;
    }

    /**
     * Scans walkable cells within {@link #DEFENDER_SPAWN_SCAN_RADIUS} of the
     * anchor, then sorts the pool by cover descending (proximity to anchor
     * breaks ties). Keeps the top {@code count}. Defenders end up tucked into
     * wall edges and building corners — "they prepared the position" emerges
     * from picking which cells they camp, not from stat asymmetry.
     */
    private static List<int[]> pickDefensiveCluster(NavigationGrid grid, int cx, int cy, int count) {
        List<int[]> pool = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy, 0});
        seen.add(key(cx, cy));
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > DEFENDER_SPAWN_SCAN_RADIUS) continue;
            if (grid.isWalkable(p[0], p[1])) pool.add(p);
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        pool.sort(Comparator
                .comparingInt((int[] p) -> -grid.getCoverAt(p[0], p[1])) // higher cover first
                .thenComparingInt(p -> p[2])); // closer to anchor first on cover ties
        List<int[]> picked = new ArrayList<>(count);
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            int[] p = pool.get(i);
            picked.add(new int[]{p[0], p[1]});
        }
        // Backfill if the scan didn't return enough — fall back to plain BFS from
        // the anchor so we never spawn fewer defenders than requested.
        if (picked.size() < count) {
            picked.addAll(pickSpawnCluster(grid, cx, cy, count - picked.size()));
        }
        return picked;
    }

    /** BFS from (cx, cy) over walkable cells, returning the first {@code count} cells in BFS order. */
    private static List<int[]> pickSpawnCluster(NavigationGrid grid, int cx, int cy, int count) {
        List<int[]> picked = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy});
        seen.add(key(cx, cy));
        while (!q.isEmpty() && picked.size() < count) {
            int[] p = q.poll();
            if (!grid.isWalkable(p[0], p[1])) continue;
            picked.add(p);
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny});
            }
        }
        return picked;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
