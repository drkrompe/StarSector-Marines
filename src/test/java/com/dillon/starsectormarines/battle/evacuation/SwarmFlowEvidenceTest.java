package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the swarm's strategic movement costs, by map size and swarm size, with
 * the flow field off, on, and on with packs moving as packs. Opt-in ({@code gradlew profileSwarmFlow}).
 *
 * <p>The battle is a generated city at each size with a small Marine column
 * walking a fixed route — the goal moves the whole time, which is what makes
 * per-body repathing expensive — and the swarm spawned at least
 * {@link #MIN_SPAWN_DISTANCE} cells from it, so the measured window is the
 * approach rather than a melee. The Marines are unarmed and healed every tick:
 * the question is what moving the swarm costs, and a battle that is decided
 * stops asking it.
 *
 * <p>Timing runs are serial, one simulation at a time on the serial scheduler.
 * The determinism check is separate: two replicas of each lever state side by
 * side in one JVM, compared hash for hash.
 */
@Tag("swarm-flow-evidence")
class SwarmFlowEvidenceTest {

    private static final long MAP_SEED = 42L;
    private static final long SPAWN_SEED = 20261021L;
    private static final int PREY_GROUPS = 3;
    private static final int PREY_PER_GROUP = 8;
    private static final int PREY_COUNT = PREY_GROUPS * PREY_PER_GROUP;
    /** A tick slower than this misses a 30 Hz frame on its own. */
    private static final long BUDGET_NANOS = 10_000_000L;
    private static final float PREY_SPEED = 2.0f;
    private static final int MIN_SPAWN_DISTANCE = 80;
    private static final int PREY_ROUTE_LENGTH = 60;
    /** A body that moved less than this over {@link #STUCK_WINDOW} ticks, out of contact, is stuck. */
    private static final float STUCK_DISTANCE = 1.5f;
    private static final int STUCK_WINDOW = 150;
    /** Runners spawn in clusters of this many, each cluster one pack. */
    private static final int PACK_SIZE = 6;

    @Test
    void reportsStrategicMovementCostBySwarmAndMapSize() throws Exception {
        int[][] sizes = parseSizes(System.getProperty(
                "battle.swarmFlow.sizes", "280x168,400x240,560x336"));
        int[] counts = parseInts(System.getProperty(
                "battle.swarmFlow.counts", "64,150,300,600,1000"));
        int warmup = Integer.getInteger("battle.swarmFlow.warmupTicks", 90);
        int measured = Integer.getInteger("battle.swarmFlow.measureTicks", 450);
        String[] levers = System.getProperty("battle.swarmFlow.levers", "off,on,pack").split(",");
        int determinismTicks = Integer.getInteger("battle.swarmFlow.determinismTicks", 600);
        Path outputDir = Path.of(System.getProperty("battle.swarmFlow.outputDir",
                "build/reports/performance/swarm-flow"));

        JSONObject report = new JSONObject();
        report.put("schemaVersion", 1);
        report.put("mapSeed", MAP_SEED);
        report.put("warmupTicks", warmup);
        report.put("measuredTicks", measured);
        report.put("minimumParallelUnits",
                System.getProperty("battle.unitUpdate.minimumParallelUnits", ""));
        JSONArray rows = new JSONArray();
        StringBuilder md = new StringBuilder();
        md.append("# Swarm strategic movement cost\n\n")
                .append("Generated city (BSP, seed ").append(MAP_SEED).append("), ")
                .append(PREY_GROUPS).append(" groups of ").append(PREY_PER_GROUP)
                .append(" Marines each walking its own ").append(PREY_ROUTE_LENGTH)
                .append("-cell route, swarm spawned >= ").append(MIN_SPAWN_DISTANCE)
                .append(" cells away. Serial scheduler, one sim at a time. ")
                .append(warmup).append(" warm-up ticks, ").append(measured)
                .append(" measured. Times are ms per tick: median [p10-p90] / p99 / max. Over: ticks over 10 ms.\n\n")
                .append("| Map | Bodies | Field | Tick ms | Over | Swarm behaviour ms | Path ms | Field build ms | Path calls/tick | Stuck | Contact | Mean dist |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (int[] size : sizes) {
            for (int count : counts) {
                for (String lever : levers) {
                    boolean flow = !lever.trim().equals("off");
                    boolean pack = lever.trim().equals("pack");
                    Run run = new Run(size[0], size[1], count, flow, pack, warmup + measured, true);
                    RunResult result = run.call();
                    JSONObject row = result.toJson(warmup);
                    rows.put(row);
                    md.append(result.markdownRow(warmup)).append('\n');
                    System.out.println("swarm-flow " + size[0] + "x" + size[1]
                            + " n=" + count + " flow=" + flow + " pack=" + pack + " " + row);
                }
            }
        }
        report.put("rows", rows);

        // Determinism: two replicas side by side, each lever state.
        JSONArray determinism = new JSONArray();
        md.append("\n## Determinism (two replicas side by side)\n\n")
                .append("| Map | Bodies | Field | Ticks | Identical | First differing tick |\n")
                .append("|---|---|---|---|---|---|\n");
        int[] dSize = sizes[Math.min(1, sizes.length - 1)];
        int dCount = counts[Math.min(1, counts.length - 1)];
        ExecutorService pool = Executors.newFixedThreadPool(2);
        boolean allIdentical = true;
        try {
            for (String lever : levers) {
                boolean flow = !lever.trim().equals("off");
                boolean pack = lever.trim().equals("pack");
                Future<RunResult> a = pool.submit(new Run(dSize[0], dSize[1], dCount, flow, pack,
                        determinismTicks, false));
                Future<RunResult> b = pool.submit(new Run(dSize[0], dSize[1], dCount, flow, pack,
                        determinismTicks, false));
                long[] ha = a.get().hashes;
                long[] hb = b.get().hashes;
                int firstDiff = -1;
                for (int i = 0; i < Math.min(ha.length, hb.length); i++) {
                    if (ha[i] != hb[i]) { firstDiff = i; break; }
                }
                boolean identical = firstDiff < 0 && ha.length == hb.length;
                allIdentical &= identical;
                determinism.put(new JSONObject()
                        .put("map", dSize[0] + "x" + dSize[1])
                        .put("bodies", dCount)
                        .put("flowField", flow)
                    .put("packs", pack)
                        .put("packs", pack)
                        .put("ticks", determinismTicks)
                        .put("identical", identical)
                        .put("firstDifferingTick", firstDiff));
                md.append("| ").append(dSize[0]).append('x').append(dSize[1])
                        .append(" | ").append(dCount).append(" | ").append(lever.trim())
                        .append(" | ").append(determinismTicks).append(" | ")
                        .append(identical ? "yes" : "NO").append(" | ")
                        .append(firstDiff < 0 ? "-" : Integer.toString(firstDiff)).append(" |\n");
            }
        } finally {
            pool.shutdownNow();
        }
        report.put("determinism", determinism);

        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("summary.json"), report.toString(2),
                StandardCharsets.UTF_8);
        Files.writeString(outputDir.resolve("summary.md"), md.toString(),
                StandardCharsets.UTF_8);
        System.out.println(md);
        System.out.println("Swarm flow evidence: " + outputDir.toAbsolutePath());
        assertTrue(allIdentical, "side-by-side replicas diverged; see summary.md");
    }

    /** One battle: generate, populate, play, and record per-tick costs and a state hash. */
    private static final class Run implements java.util.concurrent.Callable<RunResult> {
        final int width;
        final int height;
        final int count;
        final boolean flow;
        final boolean pack;
        final int ticks;
        final boolean timed;

        Run(int width, int height, int count, boolean flow, boolean pack, int ticks, boolean timed) {
            this.width = width;
            this.height = height;
            this.count = count;
            this.flow = flow;
            this.pack = pack;
            this.ticks = ticks;
            this.timed = timed;
        }

        @Override
        public RunResult call() {
            MapResult map = new BspCityGenerator().generate(width, height, MAP_SEED);
            NavigationGrid grid = map.grid;
            int[][] routes = new int[PREY_GROUPS][];
            for (int g = 0; g < PREY_GROUPS; g++) routes[g] = preyRoute(map, g);
            RunResult result = new RunResult(width, height, count, flow, pack, ticks);
            try (BattleSimulation sim = new BattleSimulation(grid, map.topology, MAP_SEED)) {
                sim.setMissionCompletionEnabled(false);
                sim.setSwarmFlowFieldEnabled(flow);
                sim.setSwarmPackEnabled(pack);
                long[] prey = new long[PREY_COUNT];
                for (int i = 0; i < PREY_COUNT; i++) {
                    int[] route = routes[i / PREY_PER_GROUP];
                    prey[i] = sim.spawn(new EntitySpec("Prey " + i, Faction.MARINE,
                            UnitType.MARINE, Paths.cellX(route, 0), Paths.cellY(route, 0)));
                }
                long[] runners = spawnRunners(sim, map, routes, count);
                // Packs are stamped on every lever, as a bore stamps them; only "pack" moves them as packs.
                for (int i = 0; i < runners.length; i += PACK_SIZE) {
                    sim.swarmPacks().form(Arrays.copyOfRange(runners, i, Math.min(runners.length, i + PACK_SIZE)), sim);
                }
                result.spawned = runners.length;
                float[][] history = new float[STUCK_WINDOW + 1][];
                for (int tick = 0; tick < ticks; tick++) {
                    placePrey(sim, prey, routes, tick);
                    long start = System.nanoTime();
                    int before = sim.getSimTickIndex();
                    while (sim.getSimTickIndex() == before) sim.advance(BattleSimulation.TICK_DT);
                    long elapsed = System.nanoTime() - start;
                    TickInnerProfile profile = sim.getTickInnerProfile();
                    result.tickNanos[tick] = elapsed;
                    result.swarmNanos[tick] = profile.nanosOf(
                            TickInnerProfile.Bucket.BEHAVIOR_SWARM_PRESSURE);
                    result.pathNanos[tick] = profile.nanosOf(TickInnerProfile.Bucket.SWARM_PATHFIND);
                    result.fieldNanos[tick] = profile.nanosOf(
                            TickInnerProfile.Bucket.SHARED_PATH_FIELD_BUILD)
                            + profile.nanosOf(TickInnerProfile.Bucket.SWARM_FLOW_FIELD_BUILD);
                    result.pathCalls[tick] = profile.countOf(TickInnerProfile.Bucket.SWARM_PATHFIND);
                    result.hashes[tick] = hash(sim, runners);
                    history[tick % history.length] = positions(sim, runners);
                }
                result.finish(sim, runners, prey, history, ticks);
            }
            return result;
        }
    }

    static final class RunResult {
        final int width;
        final int height;
        final int count;
        final boolean flow;
        final long[] tickNanos;
        final long[] swarmNanos;
        final long[] pathNanos;
        final long[] fieldNanos;
        final int[] pathCalls;
        final long[] hashes;
        int spawned;
        int stuck;
        int contact;
        int alive;
        double meanDistance;

        final boolean pack;

        RunResult(int width, int height, int count, boolean flow, boolean pack, int ticks) {
            this.pack = pack;
            this.width = width;
            this.height = height;
            this.count = count;
            this.flow = flow;
            tickNanos = new long[ticks];
            swarmNanos = new long[ticks];
            pathNanos = new long[ticks];
            fieldNanos = new long[ticks];
            pathCalls = new int[ticks];
            hashes = new long[ticks];
        }

        void finish(BattleSimulation sim, long[] runners, long[] prey,
                    float[][] history, int ticks) {
            float[] now = history[(ticks - 1) % history.length];
            float[] then = history[(ticks - 1 - STUCK_WINDOW + history.length * 4) % history.length];
            double distanceSum = 0;
            for (int i = 0; i < runners.length; i++) {
                if (sim.resolveUnit(runners[i]) == 0L) continue;
                alive++;
                float nearest = Float.MAX_VALUE;
                for (long p : prey) {
                    float dx = sim.world().x(p) - now[i * 2];
                    float dy = sim.world().y(p) - now[i * 2 + 1];
                    nearest = Math.min(nearest, (float) Math.sqrt(dx * dx + dy * dy));
                }
                distanceSum += nearest;
                boolean inContact = nearest <= 2.5f;
                if (inContact) contact++;
                if (then != null && !inContact) {
                    float mx = now[i * 2] - then[i * 2];
                    float my = now[i * 2 + 1] - then[i * 2 + 1];
                    if (Math.sqrt(mx * mx + my * my) < STUCK_DISTANCE) stuck++;
                }
            }
            meanDistance = alive == 0 ? 0 : distanceSum / alive;
        }

        JSONObject toJson(int warmup) throws Exception {
            return new JSONObject()
                    .put("map", width + "x" + height)
                    .put("bodies", count)
                    .put("spawned", spawned)
                    .put("flowField", flow)
                    .put("packs", pack)
                    .put("tickMs", stats(tickNanos, warmup))
                    .put("ticksOver10ms", overBudget(warmup))
                    .put("swarmBehaviourMs", stats(swarmNanos, warmup))
                    .put("pathMs", stats(pathNanos, warmup))
                    .put("fieldBuildMs", stats(fieldNanos, warmup))
                    .put("fieldBuildTotalMs", total(fieldNanos, warmup))
                    .put("pathCallsPerTick", meanCalls(warmup))
                    .put("stuck", stuck)
                    .put("contact", contact)
                    .put("alive", alive)
                    .put("meanDistanceToPrey", Math.round(meanDistance * 10) / 10.0);
        }

        String markdownRow(int warmup) {
            return "| " + width + "x" + height + " | " + count + " | " + (pack ? "pack" : flow ? "on" : "off")
                    + " | " + cell(tickNanos, warmup) + " | " + overBudget(warmup)
                    + " | " + cell(swarmNanos, warmup)
                    + " | " + cell(pathNanos, warmup) + " | " + cell(fieldNanos, warmup)
                    + " | " + String.format("%.1f", meanCalls(warmup))
                    + " | " + stuck + " | " + contact
                    + " | " + String.format("%.1f", meanDistance) + " |";
        }

        private int overBudget(int warmup) {
            int over = 0;
            for (int i = warmup; i < tickNanos.length; i++) if (tickNanos[i] > BUDGET_NANOS) over++;
            return over;
        }

        private double meanCalls(int warmup) {
            long sum = 0;
            for (int i = warmup; i < pathCalls.length; i++) sum += pathCalls[i];
            return pathCalls.length <= warmup ? 0 : sum / (double) (pathCalls.length - warmup);
        }

        private static String cell(long[] nanos, int warmup) {
            double[] s = percentiles(nanos, warmup);
            return String.format("%.2f [%.2f-%.2f] / %.1f / %.1f", s[1], s[0], s[2], s[4], s[3]);
        }

        private static JSONObject stats(long[] nanos, int warmup) throws Exception {
            double[] s = percentiles(nanos, warmup);
            return new JSONObject().put("p10", round(s[0])).put("median", round(s[1]))
                    .put("p90", round(s[2])).put("p99", round(s[4])).put("max", round(s[3]));
        }

        private static double total(long[] nanos, int warmup) {
            long sum = 0;
            for (int i = warmup; i < nanos.length; i++) sum += nanos[i];
            return round(sum / 1e6);
        }

        private static double[] percentiles(long[] nanos, int warmup) {
            long[] window = Arrays.copyOfRange(nanos, Math.min(warmup, nanos.length), nanos.length);
            if (window.length == 0) return new double[5];
            Arrays.sort(window);
            return new double[]{
                    window[(int) (window.length * 0.1)] / 1e6,
                    window[window.length / 2] / 1e6,
                    window[Math.min(window.length - 1, (int) (window.length * 0.9))] / 1e6,
                    window[window.length - 1] / 1e6,
                    window[Math.min(window.length - 1, (int) (window.length * 0.99))] / 1e6};
        }

        private static double round(double v) { return Math.round(v * 1000) / 1000.0; }
    }

    /**
     * A {@link #PREY_ROUTE_LENGTH}-cell walk from the Marine spawn, toward the
     * reachable cell at that distance farthest from the defenders' side.
     */
    private static int[] preyRoute(MapResult map, int group) {
        NavigationGrid grid = map.grid;
        int sx = map.marineSpawnX;
        int sy = map.marineSpawnY;
        int[] start = nearestWalkable(grid, sx, sy);
        sx = start[0];
        sy = start[1];
        int bestX = -1;
        int bestY = -1;
        double bestScore = -1;
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y)) continue;
                double d = Math.hypot(x - sx, y - sy);
                if (d < PREY_ROUTE_LENGTH - 5 || d > PREY_ROUTE_LENGTH + 5) continue;
                // Each group heads off on its own bearing, a third of a turn apart.
                double bearing = Math.atan2(y - sy, x - sx) - group * (2 * Math.PI / PREY_GROUPS);
                double away = 1 + Math.cos(bearing);
                if (away <= bestScore) continue;
                if (!grid.arePathConnected(sx, sy, x, y,
                        GridPathfinder.USE_CARDINAL_NAVIGATION)) continue;
                bestScore = away;
                bestX = x;
                bestY = y;
            }
        }
        int[] path = GridPathfinder.findPath(grid, sx, sy, bestX, bestY);
        if (Paths.isEmpty(path)) throw new IllegalStateException("no prey route");
        return path;
    }

    private static int[] nearestWalkable(NavigationGrid grid, int x, int y) {
        for (int r = 0; r < Math.max(grid.getWidth(), grid.getHeight()); r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (grid.inBounds(x + dx, y + dy) && grid.isWalkable(x + dx, y + dy)) {
                        return new int[]{x + dx, y + dy};
                    }
                }
            }
        }
        throw new IllegalStateException("no walkable cell");
    }

    /** Each Marine column walks its route out and back, members trailing one cell apart. */
    private static void placePrey(BattleSimulation sim, long[] prey, int[][] routes, int tick) {
        float travelled = tick * BattleSimulation.TICK_DT * PREY_SPEED;
        for (int i = 0; i < prey.length; i++) {
            if (sim.resolveUnit(prey[i]) == 0L) continue;
            int[] route = routes[i / PREY_PER_GROUP];
            int cells = Paths.cellCount(route);
            float along = Math.max(0f, travelled - i % PREY_PER_GROUP);
            int period = (cells - 1) * 2;
            int step = (int) along % Math.max(1, period);
            int index = step < cells ? step : period - step;
            sim.world().setCellPos(prey[i], Paths.cellX(route, index), Paths.cellY(route, index));
            sim.world().setHp(prey[i], sim.world().maxHp(prey[i]));
        }
    }

    private static long[] spawnRunners(BattleSimulation sim, MapResult map, int[][] routes, int count) {
        NavigationGrid grid = map.grid;
        int sx = Paths.cellX(routes[0], 0);
        int sy = Paths.cellY(routes[0], 0);
        List<Integer> candidates = new ArrayList<>();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y)) continue;
                if (Math.hypot(x - sx, y - sy) < MIN_SPAWN_DISTANCE) continue;
                if (nearAnyRouteEnd(routes, x, y)) continue;
                if (!grid.arePathConnected(sx, sy, x, y,
                        GridPathfinder.USE_CARDINAL_NAVIGATION)) continue;
                candidates.add(grid.index(x, y));
            }
        }
        Collections.shuffle(candidates, new Random(SPAWN_SEED));
        // Clusters of PACK_SIZE round a seed cell, as a pulse leaves a mouth: the nearest free cells to it.
        boolean[] taken = new boolean[grid.getWidth() * grid.getHeight()];
        List<Integer> cells = new ArrayList<>();
        for (int seed : candidates) {
            if (cells.size() >= count) break;
            if (taken[seed]) continue;
            int x0 = seed % grid.getWidth();
            int y0 = seed / grid.getWidth();
            int placed = 0;
            for (int r = 0; r <= 3 && placed < PACK_SIZE; r++) {
                for (int dy = -r; dy <= r && placed < PACK_SIZE; dy++) {
                    for (int dx = -r; dx <= r && placed < PACK_SIZE; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                        int x = x0 + dx;
                        int y = y0 + dy;
                        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                        int cell = grid.index(x, y);
                        if (taken[cell] || !grid.arePathConnected(x0, y0, x, y,
                                GridPathfinder.USE_CARDINAL_NAVIGATION)) continue;
                        taken[cell] = true;
                        cells.add(cell);
                        placed++;
                    }
                }
            }
        }
        long[] ids = new long[Math.min(count, cells.size())];
        for (int i = 0; i < ids.length; i++) {
            int cell = cells.get(i);
            ids[i] = sim.spawn(new EntitySpec("Swarm Runner " + (i + 1), Faction.DEFENDER,
                    UnitType.SWARM_RUNNER, cell % grid.getWidth(), cell / grid.getWidth())
                    .role(UnitRole.SWARM_PRESSURE));
        }
        return ids;
    }

    private static boolean nearAnyRouteEnd(int[][] routes, int x, int y) {
        for (int[] route : routes) {
            if (Math.hypot(x - Paths.destX(route), y - Paths.destY(route)) < MIN_SPAWN_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private static float[] positions(BattleSimulation sim, long[] runners) {
        float[] out = new float[runners.length * 2];
        for (int i = 0; i < runners.length; i++) {
            if (sim.resolveUnit(runners[i]) == 0L) continue;
            out[i * 2] = sim.world().x(runners[i]);
            out[i * 2 + 1] = sim.world().y(runners[i]);
        }
        return out;
    }

    private static long hash(BattleSimulation sim, long[] runners) {
        long h = 1125899906842597L;
        for (long runner : runners) {
            if (sim.resolveUnit(runner) == 0L) { h = h * 31 + 7; continue; }
            h = h * 31 + Float.floatToIntBits(sim.world().x(runner));
            h = h * 31 + Float.floatToIntBits(sim.world().y(runner));
            h = h * 31 + sim.combat().targetId(runner);
        }
        return h;
    }

    private static int[][] parseSizes(String value) {
        String[] parts = value.split(",");
        int[][] out = new int[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            String[] wh = parts[i].trim().split("x");
            out[i] = new int[]{Integer.parseInt(wh[0]), Integer.parseInt(wh[1])};
        }
        return out;
    }

    private static int[] parseInts(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
    }
}
