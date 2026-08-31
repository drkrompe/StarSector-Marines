package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.road.VehicleCorridor;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Dev tool dressed as a test — the validation half of the map-gen gut-check
 * loop (the preview half lives in {@link BspMapPreviewTest}). Where the preview
 * renders maps for the eye, this runs structural <em>scans</em> over the same
 * seed batches and prints a per-seed report so missing generation rules surface
 * as numbers, not as a bug noticed in-game three weeks later.
 *
 * <p>Three scans, each isolating a distinct class of "missing rule":
 *
 * <ol>
 *   <li><b>Connectivity (cell vs. edge).</b> {@link BspMapPreviewTest}'s
 *       {@code assertConnected} floods 4-neighbor over {@link
 *       NavigationGrid#isWalkable} — <em>cell</em> connectivity only. The real
 *       {@link GridPathfinder} honors per-edge walls (dual-side check, {@code
 *       GridPathfinder} L289-290). This scan floods both models and reports the
 *       delta: cells the cell-model thinks connect but the <em>edge</em> model
 *       (i.e. the pathfinder) can't cross. A non-zero delta is geometry the
 *       generator produced that units cannot actually traverse — invisible to
 *       the existing island scan. Because a diagonal move in {@link
 *       GridPathfinder} requires both adjacent cardinal edges, cardinal
 *       edge-flood reachability equals the pathfinder's full connectivity, so
 *       cardinal flooding is the exact oracle.</li>
 *   <li><b>Semantic reachability.</b> Not "do all cells connect" but "can the
 *       marine spawn actually <em>path</em> to the things that matter" — the
 *       defender spawn and every tactical node (garrisons, command posts,
 *       objectives) — using the real {@link GridPathfinder}. Catches a stranded
 *       objective / garrison and reports the assault-distance distribution
 *       (is the fight the right length?).</li>
 *   <li><b>Garrison deployability.</b> Models the real consumer
 *       ({@code BattleSetup.pickCellsNear}, {@code GARRISON_SPAWN_RADIUS = 5}):
 *       a garrison node's <em>anchor</em> is the emplacement cell — for a
 *       {@code HEAVY_TOWER} / {@code MG_NEST} / {@code FORWARD_BUNKER} /
 *       {@code GUARDPOST} that cell is the turret mount or wall crenellation
 *       and is <em>intentionally non-walkable</em> ({@code
 *       FortressWallStamper.emitHeavyTower}, {@code
 *       DefensePostStamper.emitGuardpostNode}). The squad spawns on walkable
 *       cells <em>near</em> the anchor, so the real invariant is not "anchor
 *       walkable" but "≥ 1 deployable cell within radius 5" — if zero, {@code
 *       pickCellsNear} returns empty and the allocator silently drops the squad
 *       ({@code BattleSetup} L1028-1033). That zero case is the bug class fixed
 *       by hand in {@code 53fe951}; "fewer than {@code garrisonSize} cells" is
 *       a softer "cramped garrison" signal (squad deploys under-strength).</li>
 * </ol>
 *
 * <p>Run via {@code gradlew :test --tests "*MapValidationScanTest*"}. Hard
 * invariants assert and fail the build (spawns walkable + mutually reachable,
 * edge/cell connectivity agree, every garrison node has ≥ 1 reachable
 * deployable cell); softer findings print as a report for the human (cramped
 * garrisons, emplacement anchors on structure cells). As the corridor work
 * lands, the reported findings are the candidates to promote to asserts. This
 * is the standing structural acceptance harness described by
 * {@code mapgen-nouns.md}.
 */
public class MapValidationScanTest {

    private static final int GRID_W = 80;
    private static final int GRID_H = 80;
    private static final long[] LEGACY_SEEDS = { 1L, 42L, 100L, 777L, 1234L, 9999L };

    private static final int CONQUEST_W = 240;
    private static final int CONQUEST_H = 160;
    private static final long[] CONQUEST_SEEDS = { 1L, 42L, 100L, 777L };

    private static final long[] STATION_SEEDS = { 1L, 42L, 100L, 777L, 1234L, 9999L };

    /**
     * Manhattan radius the garrison allocator sweeps around a node anchor for
     * spawn cells — mirrors {@code BattleSetup.GARRISON_SPAWN_RADIUS} (kept in
     * sync by hand; the production constant is private). The deployability scan
     * counts walkable cells in this diamond as a faithful proxy for
     * {@code pickCellsNear}. It is an <em>upper bound</em>: the real picker
     * additionally filters to cells in the anchor's reachable zone, so a count
     * here is "at most this many deployable" — a zero is a hard "cannot deploy".
     */
    private static final int GARRISON_SPAWN_RADIUS = 5;

    /** Cells inward from the border a convoy stages at — mirrors {@code ConvoyMeans.PERIMETER_STAGING_INSET}. */
    private static final int CONVOY_STAGING_INSET = 2;

    /**
     * Drivable lines a {@link VehicleCorridor} must keep at its narrowest point.
     * A {@code HEAVY_APC} erodes to a radius-1 footprint, so the corridor's five
     * walkable cells leave exactly three cells a hull fits on. Asserting three
     * rather than one is the whole point of the scan: one is what a merely
     * <em>connected</em> road gives, and one is what shipped — the fortress kept a
     * street every connectivity check called preserved and no vehicle could use.
     */
    private static final int CORRIDOR_MIN_DRIVABLE_LINES = 3;

    @Test
    void scanLegacyBatch() {
        runBatch("LEGACY", LEGACY_SEEDS, GRID_W, GRID_H, null);
    }

    @Test
    void scanConquestBatch() {
        for (TraversalAxis axis : TraversalAxis.values()) {
            runBatch("CONQUEST/" + axis, CONQUEST_SEEDS, CONQUEST_W, CONQUEST_H, axis);
        }
    }

    /**
     * Station-interior scan — the noun model's connectivity acceptance gate.
     * The station inverts the city (solid-default; rooms + corridors
     * carved), so the load-bearing invariant is that the corridor spine actually
     * connects every room: <b>exactly one</b> walkable component (no islands),
     * and the marine spawn can <em>path</em> to the defender spawn via the real
     * {@link GridPathfinder}. Corridors are cell-based here (no edge-doors yet),
     * so the cell/edge models still agree — the edge scan stays the armed no-op
     * it is for the city.
     */
    @Test
    void scanStationBatch() {
        BspCityGenerator gen = new BspCityGenerator();
        List<String> failures = new ArrayList<>();

        System.out.println("=== Map validation scan: STATION (" + GRID_W + "x" + GRID_H + ") ===");
        for (long seed : STATION_SEEDS) {
            MapResult map = gen.generateStation(GRID_W, GRID_H, seed);
            TacticalMap tactical = gen.getLastTacticalMap();

            System.out.println("\n-- seed " + seed + " --");

            ConnectivityResult conn = scanConnectivity(map.grid);
            System.out.println(conn.report());

            ReachabilityResult reach = scanReachability(map, tactical);
            System.out.println(reach.report());

            NavigationGrid grid = map.grid;
            if (!grid.isWalkable(map.marineSpawnX, map.marineSpawnY)) {
                failures.add("STATION seed " + seed + ": marine spawn on non-walkable cell ("
                        + map.marineSpawnX + "," + map.marineSpawnY + ")");
            }
            if (!grid.isWalkable(map.defenderSpawnX, map.defenderSpawnY)) {
                failures.add("STATION seed " + seed + ": defender spawn on non-walkable cell ("
                        + map.defenderSpawnX + "," + map.defenderSpawnY + ")");
            }
            if (!reach.defenderReachable) {
                failures.add("STATION seed " + seed
                        + ": defender spawn UNREACHABLE from marine spawn (real pathfinder)");
            }
            if (conn.cellComponents != 1) {
                failures.add("STATION seed " + seed + ": walkable space has " + conn.cellComponents
                        + " components (expected 1 — corridor spine left island rooms)");
            }
            if (conn.edgeComponents != conn.cellComponents) {
                failures.add("STATION seed " + seed + ": cell/edge connectivity disagree — "
                        + conn.cellComponents + " cell-components vs " + conn.edgeComponents
                        + " edge-components");
            }
        }

        if (!failures.isEmpty()) {
            fail("STATION scan found " + failures.size() + " invariant violation(s):\n  "
                    + String.join("\n  ", failures));
        }
        System.out.println("\nSTATION: all hard invariants held.");
    }

    /**
     * Concentric "onion" station scan — the gates must actually connect every
     * ring to the core. Same gate as the BSP station (one walkable component, no
     * islands; marine→defender pathable via the real {@link GridPathfinder}), but
     * here the defender is the core, so this proves the assault can reach the
     * core through the ring walls.
     */
    @Test
    void scanConcentricStationBatch() {
        BspCityGenerator gen = new BspCityGenerator();
        List<String> failures = new ArrayList<>();

        System.out.println("=== Map validation scan: CONCENTRIC (" + GRID_W + "x" + GRID_H + ") ===");
        for (long seed : STATION_SEEDS) {
            MapResult map = gen.generateConcentricStation(GRID_W, GRID_H, seed);
            TacticalMap tactical = gen.getLastTacticalMap();

            System.out.println("\n-- seed " + seed + " --");

            ConnectivityResult conn = scanConnectivity(map.grid);
            System.out.println(conn.report());
            ReachabilityResult reach = scanReachability(map, tactical);
            System.out.println(reach.report());

            NavigationGrid grid = map.grid;
            if (!grid.isWalkable(map.marineSpawnX, map.marineSpawnY)) {
                failures.add("CONCENTRIC seed " + seed + ": marine spawn on non-walkable cell");
            }
            if (!grid.isWalkable(map.defenderSpawnX, map.defenderSpawnY)) {
                failures.add("CONCENTRIC seed " + seed + ": defender (core) spawn on non-walkable cell");
            }
            if (!reach.defenderReachable) {
                failures.add("CONCENTRIC seed " + seed + ": core UNREACHABLE from the outer ring (real pathfinder)");
            }
            if (conn.cellComponents != 1) {
                failures.add("CONCENTRIC seed " + seed + ": walkable space has " + conn.cellComponents
                        + " components (expected 1 — a ring was left ungated)");
            }
            if (conn.edgeComponents != conn.cellComponents) {
                failures.add("CONCENTRIC seed " + seed + ": cell/edge connectivity disagree");
            }
        }

        if (!failures.isEmpty()) {
            fail("CONCENTRIC scan found " + failures.size() + " invariant violation(s):\n  "
                    + String.join("\n  ", failures));
        }
        System.out.println("\nCONCENTRIC: all hard invariants held.");
    }

    /**
     * Diamond defense-station scan — the cardinal spokes must connect every
     * isolated port through to the core. One walkable component (no port left a
     * dead pod), marine→core pathable via the real {@link GridPathfinder}.
     */
    @Test
    void scanDiamondStationBatch() {
        BspCityGenerator gen = new BspCityGenerator();
        List<String> failures = new ArrayList<>();

        System.out.println("=== Map validation scan: DIAMOND (" + GRID_W + "x" + GRID_H + ") ===");
        for (long seed : STATION_SEEDS) {
            MapResult map = gen.generateDiamondStation(GRID_W, GRID_H, seed);
            TacticalMap tactical = gen.getLastTacticalMap();

            System.out.println("\n-- seed " + seed + " --");

            ConnectivityResult conn = scanConnectivity(map.grid);
            System.out.println(conn.report());
            ReachabilityResult reach = scanReachability(map, tactical);
            System.out.println(reach.report());

            NavigationGrid grid = map.grid;
            if (!grid.isWalkable(map.marineSpawnX, map.marineSpawnY)) {
                failures.add("DIAMOND seed " + seed + ": marine spawn on non-walkable cell");
            }
            if (!grid.isWalkable(map.defenderSpawnX, map.defenderSpawnY)) {
                failures.add("DIAMOND seed " + seed + ": defender (core) spawn on non-walkable cell");
            }
            if (!reach.defenderReachable) {
                failures.add("DIAMOND seed " + seed + ": core UNREACHABLE from the port (real pathfinder)");
            }
            if (conn.cellComponents != 1) {
                failures.add("DIAMOND seed " + seed + ": walkable space has " + conn.cellComponents
                        + " components (expected 1 — a port/spoke was left disconnected)");
            }
            if (conn.edgeComponents != conn.cellComponents) {
                failures.add("DIAMOND seed " + seed + ": cell/edge connectivity disagree");
            }
        }

        if (!failures.isEmpty()) {
            fail("DIAMOND scan found " + failures.size() + " invariant violation(s):\n  "
                    + String.join("\n  ", failures));
        }
        System.out.println("\nDIAMOND: all hard invariants held.");
    }

    /**
     * Generates each seed, runs the three scans, prints the report, and collects
     * hard-invariant violations across the whole batch (so one bad seed doesn't
     * mask the others). Throws once at the end if any seed failed an invariant.
     */
    private void runBatch(String label, long[] seeds, int w, int h, TraversalAxis axis) {
        BspCityGenerator gen = new BspCityGenerator();
        List<String> failures = new ArrayList<>();

        System.out.println("=== Map validation scan: " + label + " (" + w + "x" + h + ") ===");
        for (long seed : seeds) {
            MapResult map = (axis == null)
                    ? gen.generate(w, h, seed)
                    : gen.generate(w, h, seed, axis);
            TacticalMap tactical = gen.getLastTacticalMap();

            System.out.println("\n-- seed " + seed + " --");

            ConnectivityResult conn = scanConnectivity(map.grid);
            System.out.println(conn.report());

            ReachabilityResult reach = scanReachability(map, tactical);
            System.out.println(reach.report());

            // ----- hard invariants -----
            NavigationGrid grid = map.grid;
            if (!grid.isWalkable(map.marineSpawnX, map.marineSpawnY)) {
                failures.add(label + " seed " + seed + ": marine spawn on non-walkable cell ("
                        + map.marineSpawnX + "," + map.marineSpawnY + ")");
            }
            if (!grid.isWalkable(map.defenderSpawnX, map.defenderSpawnY)) {
                failures.add(label + " seed " + seed + ": defender spawn on non-walkable cell ("
                        + map.defenderSpawnX + "," + map.defenderSpawnY + ")");
            }
            if (!reach.defenderReachable) {
                failures.add(label + " seed " + seed
                        + ": defender spawn UNREACHABLE from marine spawn (real pathfinder)");
            }
            for (String u : reach.undeployable) {
                failures.add(label + " seed " + seed + ": garrison " + u);
            }
            for (String u : reach.unreachable) {
                failures.add(label + " seed " + seed + ": garrison " + u);
            }
            CorridorResult corridor = scanVehicleCorridor(map);
            if (corridor != null) {
                System.out.println(corridor.report());
                if (corridor.narrowest < CORRIDOR_MIN_DRIVABLE_LINES) {
                    failures.add(label + " seed " + seed + ": vehicle corridor pinches to "
                            + corridor.narrowest + " drivable line(s) at forward "
                            + corridor.narrowestAt + " (need "
                            + CORRIDOR_MIN_DRIVABLE_LINES + ")");
                }
                if (!corridor.entryDrivable) {
                    failures.add(label + " seed " + seed
                            + ": vehicle corridor's rear entry (" + map.vehicleCorridor.entryX
                            + "," + map.vehicleCorridor.entryY + ") is not drivable - a ground"
                            + " reinforcement has nowhere to come on at");
                }
                if (!corridor.spansMap) {
                    failures.add(label + " seed " + seed + ": vehicle corridor does not connect"
                            + " the rear entry to the attacker edge - a convoy can enter and"
                            + " cannot reach the city");
                }
            }
            if (conn.edgeComponents != conn.cellComponents) {
                failures.add(label + " seed " + seed + ": cell/edge connectivity disagree — "
                        + conn.cellComponents + " cell-components vs " + conn.edgeComponents
                        + " edge-components (" + conn.edgeIsolatedFromCellMain
                        + " cells walkable+cell-connected but edge-isolated; pathfinder cannot cross)");
            }
        }

        if (!failures.isEmpty()) {
            fail(label + " scan found " + failures.size() + " invariant violation(s):\n  "
                    + String.join("\n  ", failures));
        }
        System.out.println("\n" + label + ": all hard invariants held.");
    }

    // ===================== Vehicle corridor scan =====================

    /**
     * Does the map's authored vehicle corridor actually carry a vehicle?
     *
     * <p>Measured with the same primitives the convoy uses — {@link
     * VehicleClearance#erode} at the {@code HEAVY_APC}'s own footprint radius —
     * rather than with cell walkability, because walkable and drivable are the
     * two things this whole layer exists to stop conflating.
     */
    private static final class CorridorResult {
        int narrowest = Integer.MAX_VALUE;
        int narrowestAt = -1;
        boolean entryDrivable;
        boolean spansMap;

        String report() {
            return "  vehicle corridor: narrowest " + narrowest + " drivable line(s) at forward "
                    + narrowestAt + " | rear entry " + (entryDrivable ? "drivable" : "BLOCKED")
                    + " | " + (spansMap ? "spans to the attacker edge" : "DOES NOT SPAN");
        }
    }

    /** Null when this map family has no corridor (legacy, station, or the corridor switched off). */
    private static CorridorResult scanVehicleCorridor(MapResult map) {
        VehicleCorridor corridor = map.vehicleCorridor;
        if (corridor == null) return null;
        int w = map.grid.getWidth(), h = map.grid.getHeight();
        boolean alongY = corridor.axis == TraversalAxis.SOUTH_TO_NORTH;
        VehicleClearance clearance = VehicleClearance.erode(map.grid,
                VehicleClearance.radiusForWidth(VehicleType.HEAVY_APC.visualWidthCells));

        CorridorResult result = new CorridorResult();
        int extent = alongY ? h : w;
        for (int f = 0; f < extent; f++) {
            // The border rows are the map's own impassable ring. The corridor
            // terminates on them by design, so the wall stamper's seal pass sees
            // it as edge-connected, but no hull ever stands there.
            if (f < CONVOY_STAGING_INSET || f >= extent - CONVOY_STAGING_INSET) continue;
            int lines = 0;
            for (int l = 0; l < (alongY ? w : h); l++) {
                int x = alongY ? l : f, y = alongY ? f : l;
                if (corridor.contains(x, y) && clearance.isPassable(x, y)) lines++;
            }
            if (lines < result.narrowest) { result.narrowest = lines; result.narrowestAt = f; }
        }
        if (result.narrowest == Integer.MAX_VALUE) result.narrowest = 0;

        int ex = alongY ? corridor.entryX : corridor.entryX - CONVOY_STAGING_INSET;
        int ey = alongY ? corridor.entryY - CONVOY_STAGING_INSET : corridor.entryY;
        result.entryDrivable = clearance.isPassable(ex, ey);
        result.spansMap = result.entryDrivable
                && floodReachesAttackerEdge(clearance, ex, ey, w, h, alongY);
        return result;
    }

    /** True if a hull put down at the rear entry can drive to the attacker's edge of the map. */
    private static boolean floodReachesAttackerEdge(VehicleClearance clearance,
                                                    int startX, int startY,
                                                    int w, int h, boolean alongY) {
        boolean[] seen = new boolean[w * h];
        Deque<int[]> queue = new ArrayDeque<>();
        seen[startY * w + startX] = true;
        queue.add(new int[]{ startX, startY });
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            if ((alongY ? cell[1] : cell[0]) <= CONVOY_STAGING_INSET) return true;
            for (int[] step : new int[][]{ { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } }) {
                int nx = cell[0] + step[0], ny = cell[1] + step[1];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (seen[ny * w + nx] || !clearance.isPassable(nx, ny)) continue;
                seen[ny * w + nx] = true;
                queue.add(new int[]{ nx, ny });
            }
        }
        return false;
    }

    // ===================== Connectivity scan (cell vs. edge) =====================

    private static final class ConnectivityResult {
        int cellComponents;
        int edgeComponents;
        int cellMainSize;
        int edgeMainSize;
        int totalWalkable;
        /** Cells in the cell-model main component that the edge model can't reach from the edge-model main start. */
        int edgeIsolatedFromCellMain;
        List<int[]> sampleIsolated = new ArrayList<>();

        String report() {
            StringBuilder sb = new StringBuilder();
            sb.append("  connectivity: ").append(totalWalkable).append(" walkable cells | ")
              .append("cell-model ").append(cellComponents).append(" comp (main ").append(cellMainSize).append(") | ")
              .append("edge-model ").append(edgeComponents).append(" comp (main ").append(edgeMainSize).append(")");
            if (edgeIsolatedFromCellMain > 0) {
                sb.append("\n    !! ").append(edgeIsolatedFromCellMain)
                  .append(" cell(s) walkable+cell-connected but EDGE-ISOLATED (pathfinder cannot cross). e.g. ");
                for (int i = 0; i < sampleIsolated.size(); i++) {
                    int[] c = sampleIsolated.get(i);
                    sb.append(c[0]).append(",").append(c[1]);
                    if (i < sampleIsolated.size() - 1) sb.append(" ");
                }
            }
            return sb.toString();
        }
    }

    private static ConnectivityResult scanConnectivity(NavigationGrid grid) {
        int w = grid.getWidth(), h = grid.getHeight();
        ConnectivityResult r = new ConnectivityResult();

        boolean[][] cellSeen = new boolean[w][h];
        boolean[][] edgeSeen = new boolean[w][h];
        int firstX = -1, firstY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                r.totalWalkable++;
                if (firstX < 0) { firstX = x; firstY = y; }
            }
        }
        if (firstX < 0) return r; // empty map

        // Component counting under both models.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                if (!cellSeen[x][y]) {
                    int size = flood(grid, x, y, cellSeen, false);
                    r.cellComponents++;
                    r.cellMainSize = Math.max(r.cellMainSize, size);
                }
                if (!edgeSeen[x][y]) {
                    int size = flood(grid, x, y, edgeSeen, true);
                    r.edgeComponents++;
                    r.edgeMainSize = Math.max(r.edgeMainSize, size);
                }
            }
        }

        // The interesting delta: cells reachable from the cell-model start under
        // the cell model, but NOT under the edge model. We re-flood the edge
        // model from the same start and diff against a cell-model flood.
        boolean[][] cellFromStart = new boolean[w][h];
        boolean[][] edgeFromStart = new boolean[w][h];
        flood(grid, firstX, firstY, cellFromStart, false);
        flood(grid, firstX, firstY, edgeFromStart, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (cellFromStart[x][y] && !edgeFromStart[x][y]) {
                    r.edgeIsolatedFromCellMain++;
                    if (r.sampleIsolated.size() < 8) r.sampleIsolated.add(new int[]{x, y});
                }
            }
        }
        return r;
    }

    /**
     * Cardinal flood from {@code (sx,sy)} marking {@code seen}. When {@code
     * edgeAware} is true a step is permitted only if the shared edge is passable
     * on both sides — the same test the {@link GridPathfinder} applies — so the
     * result is exactly the set of cells the pathfinder can reach. Returns the
     * component size.
     */
    private static int flood(NavigationGrid grid, int sx, int sy, boolean[][] seen, boolean edgeAware) {
        int w = grid.getWidth(), h = grid.getHeight();
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{sx, sy});
        seen[sx][sy] = true;
        int size = 1;
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int cx = p[0], cy = p[1];
            for (Direction d : Direction.CARDINALS) {
                int nx = cx + d.dx, ny = cy + d.dy;
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (seen[nx][ny] || !grid.isWalkable(nx, ny)) continue;
                if (edgeAware) {
                    if (!grid.isEdgePassable(cx, cy, d)) continue;
                    if (!grid.isEdgePassable(nx, ny, d.opposite())) continue;
                }
                seen[nx][ny] = true;
                size++;
                stack.push(new int[]{nx, ny});
            }
        }
        return size;
    }

    // ===================== Semantic reachability scan =====================

    private static final class ReachabilityResult {
        boolean defenderReachable;
        int defenderPathCells;
        int nodeCount;
        int garrisonNodeCount;
        int garrisonNodesReachable;
        int minNodeDist = Integer.MAX_VALUE;
        int maxNodeDist;
        int medianNodeDist;
        int anchorsOnStructure;
        EnumMap<TacticalNode.Kind, Integer> anchorsOnStructureByKind = new EnumMap<>(TacticalNode.Kind.class);
        /** garrisonSize > 0 nodes with zero deployable cells in radius — the real bug. */
        List<String> undeployable = new ArrayList<>();
        /** garrisonSize > 0 nodes with fewer deployable cells than garrisonSize — under-strength. */
        List<String> cramped = new ArrayList<>();
        List<String> unreachable = new ArrayList<>();

        String report() {
            StringBuilder sb = new StringBuilder();
            sb.append("  deployability: defender ")
              .append(defenderReachable ? ("OK (" + defenderPathCells + " cells)") : "UNREACHABLE")
              .append(" | garrison nodes ").append(garrisonNodesReachable).append("/").append(garrisonNodeCount)
              .append(" deployable+reachable");
            if (garrisonNodesReachable > 0) {
                sb.append(" | assault dist min/med/max = ")
                  .append(minNodeDist == Integer.MAX_VALUE ? "-" : minNodeDist)
                  .append("/").append(medianNodeDist).append("/").append(maxNodeDist).append(" cells");
            }
            if (anchorsOnStructure > 0) {
                sb.append("\n    info: ").append(anchorsOnStructure)
                  .append(" emplacement anchor(s) on structure (non-walkable) cells — by design, garrison spawns within radius ")
                  .append(GARRISON_SPAWN_RADIUS).append("; by kind: ").append(anchorsOnStructureByKind);
            }
            for (String f : cramped)      sb.append("\n    ~  cramped: ").append(f);
            for (String f : undeployable) sb.append("\n    !! UNDEPLOYABLE: ").append(f);
            for (String f : unreachable)  sb.append("\n    !! UNREACHABLE: ").append(f);
            return sb.toString();
        }
    }

    private static ReachabilityResult scanReachability(MapResult map, TacticalMap tactical) {
        NavigationGrid grid = map.grid;
        ReachabilityResult r = new ReachabilityResult();
        int msx = map.marineSpawnX, msy = map.marineSpawnY;

        int[] dPath = GridPathfinder.findPath(grid, msx, msy, map.defenderSpawnX, map.defenderSpawnY);
        r.defenderReachable = dPath.length > 0;
        r.defenderPathCells = dPath.length / 2;

        List<Integer> dists = new ArrayList<>();
        if (tactical != null) {
            for (TacticalNode n : tactical.all()) {
                r.nodeCount++;
                if (!grid.isWalkable(n.anchorX, n.anchorY)) {
                    r.anchorsOnStructure++;
                    r.anchorsOnStructureByKind.merge(n.kind, 1, Integer::sum);
                }
                // Only garrison-bearing nodes need deployable cells; GATE /
                // OBJECTIVE / BEACHHEAD etc. carry no squad to place.
                if (n.garrisonSize <= 0) continue;
                r.garrisonNodeCount++;

                // Model BattleSetup.pickCellsNear: walkable cells within the
                // GARRISON_SPAWN_RADIUS Manhattan diamond around the anchor.
                List<int[]> deployable = walkableCellsWithin(grid, n.anchorX, n.anchorY, GARRISON_SPAWN_RADIUS);
                String tag = n.kind + " @" + n.anchorX + "," + n.anchorY + " (garrison " + n.garrisonSize + ")";
                if (deployable.isEmpty()) {
                    r.undeployable.add(tag + " — 0 walkable cells within radius " + GARRISON_SPAWN_RADIUS
                            + "; pickCellsNear would return empty, squad silently dropped");
                    continue;
                }
                if (deployable.size() < n.garrisonSize) {
                    r.cramped.add(tag + " — only " + deployable.size() + " deployable cell(s)");
                }
                // Reachability + assault distance: path to the closest deployable
                // cell (deployable is sorted nearest-first).
                int[] goal = deployable.get(0);
                int[] path = GridPathfinder.findPath(grid, msx, msy, goal[0], goal[1]);
                if (path.length > 0) {
                    r.garrisonNodesReachable++;
                    int cells = path.length / 2;
                    dists.add(cells);
                    r.minNodeDist = Math.min(r.minNodeDist, cells);
                    r.maxNodeDist = Math.max(r.maxNodeDist, cells);
                } else {
                    r.unreachable.add(tag + " — deployable cell " + goal[0] + "," + goal[1]
                            + " not reachable from marine spawn (real pathfinder)");
                }
            }
        }
        if (!dists.isEmpty()) {
            dists.sort(Integer::compareTo);
            r.medianNodeDist = dists.get(dists.size() / 2);
        }
        return r;
    }

    /**
     * Walkable cells within Manhattan radius {@code maxR} of {@code (x,y)},
     * sorted nearest-first — the deployability proxy for
     * {@code BattleSetup.pickCellsNear} (minus the production picker's
     * reachable-zone filter and cover sort, neither of which changes whether a
     * cell <em>exists</em> to spawn on).
     */
    private static List<int[]> walkableCellsWithin(NavigationGrid grid, int x, int y, int maxR) {
        int w = grid.getWidth(), h = grid.getHeight();
        List<int[]> out = new ArrayList<>();
        for (int ny = y - maxR; ny <= y + maxR; ny++) {
            for (int nx = x - maxR; nx <= x + maxR; nx++) {
                int dist = Math.abs(nx - x) + Math.abs(ny - y);
                if (dist > maxR) continue;
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (grid.isWalkable(nx, ny)) out.add(new int[]{nx, ny, dist});
            }
        }
        out.sort((a, b) -> Integer.compare(a[2], b[2]));
        return out;
    }
}
