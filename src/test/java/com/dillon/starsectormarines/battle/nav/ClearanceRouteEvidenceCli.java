package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Opt-in pure-route and asynchronous coordinator evidence, without a running battle. */
public final class ClearanceRouteEvidenceCli {
    private static final int WIDTH = 560;
    private static final int HEIGHT = 336;
    private static final int BUDGET = 32768;
    private static final int GENERATED_BUDGET = 1_000_000;
    private static final long DEADLINE_NANOS = 10_000_000_000L;
    private static final long GENERATED_SEED = 42L;

    private ClearanceRouteEvidenceCli() {}

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        ClearanceRoutePlanner planner = new ClearanceRoutePlanner();
        NavigationGrid floor = floor();
        // Warm the solver before reporting elapsed values. Counts, rather than
        // wall times, are the portable evidence of how much work was performed.
        for (int i = 0; i < 8; i++) planner.findRoute(floor, 4.5f, 4.5f,
                40.5f, 25.5f, .6f, .75f, BUDGET);
        StringBuilder csv = new StringBuilder("case,queries,found,limited,expanded,clearance_checks,total_ms,worst_ms\n");
        StringBuilder sliced = new StringBuilder("case,status,steps,expanded,worst_step_expanded,total_ms,worst_step_ms\n");
        measure(csv, "open-long", planner, floor, 4.5f, 4.5f, 550.5f, 325.5f, 1);
        for (int y = 0; y < HEIGHT - 12; y++) floor.setWalkable(280, y, false);
        measure(csv, "long-detour", planner, floor, 260.5f, 8.5f, 300.5f, 8.5f, 1);
        measureSliced(sliced, "long-detour", planner, floor);
        for (int y = HEIGHT - 12; y < HEIGHT; y++) floor.setWalkable(280, y, false);
        measure(csv, "sealed-region", planner, floor, 260.5f, 8.5f, 300.5f, 8.5f, 1);
        measureSliced(sliced, "sealed-region", planner, floor);
        measure(csv, "repeated-local-probes", planner, floor, 30.5f, 30.5f, 65.5f, 50.5f, 32);
        String report = csv.toString();
        Files.writeString(output.resolve("summary.csv"), report, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("sliced.csv"), sliced, StandardCharsets.UTF_8);
        System.out.print(report);
        System.out.print(sliced);
        SceneRegistries.installArmoury(Path.of("."));
        String generated = measureGenerated(planner);
        Files.writeString(output.resolve("generated.csv"), generated, StandardCharsets.UTF_8);
        System.out.print(generated);
        System.out.println("Timings are host-local diagnostics. Generated rows use seed 42 Conquest cities; "
                + "they measure isolated route queries, not battle performance. Pure queries allow one million "
                + "expansions; async queries use the runtime coordinator budget. Both stop polling after ten "
                + "seconds (a running pure slice may overrun). Poll timings exclude the 1 ms wait; latency includes it. "
                + "Start selection is the nearest legal half-cell point within eight cells of the marine spawn.");
    }

    private static String measureGenerated(ClearanceRoutePlanner planner) throws InterruptedException {
        StringBuilder csv = new StringBuilder("axis,seed,variant,radius,spawn_x,spawn_y,start_x,start_y,goal_x,goal_y,"
                + "case,status,expanded,clearance_checks,calls,total_call_ms,worst_call_ms,latency_ms,reused_proofs,expansion_budget\n");
        for (TraversalAxis axis : new TraversalAxis[]{TraversalAxis.SOUTH_TO_NORTH, TraversalAxis.WEST_TO_EAST}) {
            MapResult map = new BspCityGenerator().generate(WIDTH, HEIGHT, GENERATED_SEED, axis);
            long started = System.nanoTime();
            NavigationGrid frozen = map.grid.copyVehicleRoutingTopology();
            long snapshotNanos = System.nanoTime() - started;
            row(csv, axis, map, null, null, "cold-snapshot", "COPIED", null,
                    1, snapshotNanos, snapshotNanos, snapshotNanos, 0, 0);
            for (MechVariant variant : MechVariant.values()) {
                ClearanceRoutePlanner.Point start = legalStart(frozen, map.marineSpawnX + .5f,
                        map.marineSpawnY + .5f, variant.radius);
                if (start == null) {
                    row(csv, axis, map, variant, null, "long-route", "NO_LEGAL_START", null,
                            0, 0, 0, 0, 0, GENERATED_BUDGET);
                    continue;
                }
                long wallStart = System.nanoTime(), total = 0, worst = 0;
                int calls = 0;
                try (var search = planner.begin(frozen, start.x(), start.y(),
                        map.defenderSpawnX + .5f, map.defenderSpawnY + .5f,
                        variant.radius, .75f, GENERATED_BUDGET)) {
                    while (search.result().status() == ClearanceRoutePlanner.Status.PENDING
                            && System.nanoTime() - wallStart < DEADLINE_NANOS) {
                        started = System.nanoTime();
                        search.step(256);
                        long elapsed = System.nanoTime() - started;
                        total += elapsed;
                        worst = Math.max(worst, elapsed);
                        calls++;
                    }
                    var proof = search.result();
                    String status = proof.status() == ClearanceRoutePlanner.Status.PENDING
                            ? "PENDING_TIMEOUT" : proof.status().name();
                    row(csv, axis, map, variant, start, "pure-long", status, proof,
                            calls, total, worst, System.nanoTime() - wallStart, 0, GENERATED_BUDGET);
                }
                measureAsync(csv, axis, map, variant, start);
            }
            measureCompetingBatch(csv, axis, map, legalStart(frozen, map.marineSpawnX + .5f,
                    map.marineSpawnY + .5f, MechVariant.BULWARK.radius));
        }
        return csv.toString();
    }

    private static void measureCompetingBatch(StringBuilder csv, TraversalAxis axis, MapResult map,
                                              ClearanceRoutePlanner.Point start) throws InterruptedException {
        final int owners = 12;
        if (start == null) {
            row(csv, axis, map, MechVariant.BULWARK, null, "competing-12-total", "NO_LEGAL_START", null,
                    0, 0, 0, 0, 0, 0);
            return;
        }
        // Owner identities compete for admission even though the requested route
        // is identical. Every pending owner is reoffered, as a retained AI intent is.
        try (AsyncClearanceRoutes routes = new AsyncClearanceRoutes(true)) {
            var request = new AsyncClearanceRoutes.Request(start.x(), start.y(),
                    map.defenderSpawnX, map.defenderSpawnY, MechVariant.BULWARK.radius);
            AsyncClearanceRoutes.Reply[] replies = new AsyncClearanceRoutes.Reply[owners];
            int[] calls = new int[owners];
            long[] total = new long[owners], worst = new long[owners], latency = new long[owners];
            long wallStart = System.nanoTime();
            int terminal = 0;
            while (terminal < owners && System.nanoTime() - wallStart < DEADLINE_NANOS) {
                for (int i = 0; i < owners; i++) {
                    if (replies[i] != null && replies[i].status() != PathRequestStatus.PENDING) continue;
                    if (System.nanoTime() - wallStart >= DEADLINE_NANOS) break;
                    long started = System.nanoTime();
                    replies[i] = routes.pollOrSubmit(i + 1L, request, map.grid);
                    long elapsed = System.nanoTime() - started;
                    total[i] += elapsed;
                    worst[i] = Math.max(worst[i], elapsed);
                    calls[i]++;
                    if (replies[i].status() != PathRequestStatus.PENDING) {
                        latency[i] = System.nanoTime() - wallStart;
                        terminal++;
                    }
                }
                if (terminal < owners) Thread.sleep(1);
            }
            long batchLatency = System.nanoTime() - wallStart;
            long totalCallsNanos = 0, worstCallNanos = 0, expanded = 0, checks = 0;
            int totalCalls = 0, ready = 0, failed = 0;
            for (int i = 0; i < owners; i++) {
                var reply = replies[i];
                var proof = reply == null ? null : reply.proof();
                String status = reply == null || reply.status() == PathRequestStatus.PENDING
                        ? "PENDING_TIMEOUT" : proof == null ? reply.status().name() : proof.status().name();
                row(csv, axis, map, MechVariant.BULWARK, start, "competing-12-owner-" + (i + 1),
                        status, proof, calls[i], total[i], worst[i], latency[i] == 0 ? batchLatency : latency[i], 0, 0);
                totalCalls += calls[i];
                totalCallsNanos += total[i];
                worstCallNanos = Math.max(worstCallNanos, worst[i]);
                if (proof != null) {
                    expanded += proof.expandedNodes();
                    checks += proof.clearanceChecks();
                }
                if (reply != null && reply.status() == PathRequestStatus.READY) ready++;
                if (reply != null && reply.status() == PathRequestStatus.FAILED) failed++;
            }
            // Pending frontiers expose no counters. On timeout these totals are
            // explicitly the completed proofs' work, not the whole batch's work.
            String status = "TERMINAL_" + terminal + "_READY_" + ready + "_FAILED_" + failed
                    + "_PENDING_" + (owners - terminal);
            row(csv, axis, map, MechVariant.BULWARK, start, "competing-12-total", status,
                    expanded, checks, totalCalls, totalCallsNanos, worstCallNanos, batchLatency, 0, 0);
        }
    }

    private static void measureAsync(StringBuilder csv, TraversalAxis axis, MapResult map,
                                     MechVariant variant, ClearanceRoutePlanner.Point start)
            throws InterruptedException {
        // A fresh coordinator measures the actual cold submit, including its own
        // snapshot and worker startup. The separate snapshot row isolates copy cost.
        try (AsyncClearanceRoutes routes = new AsyncClearanceRoutes(true)) {
            var request = new AsyncClearanceRoutes.Request(start.x(), start.y(),
                    map.defenderSpawnX, map.defenderSpawnY, variant.radius);
            long wallStart = System.nanoTime();
            var reply = routes.pollOrSubmit(1L, request, map.grid);
            long submitNanos = System.nanoTime() - wallStart;
            row(csv, axis, map, variant, start, "async-cold-submit", reply.status().name(),
                    reply.proof(), 1, submitNanos, submitNanos, submitNanos, 0, 0);
            long total = 0, worst = 0;
            int calls = 0;
            while (reply.status() == PathRequestStatus.PENDING
                    && System.nanoTime() - wallStart < DEADLINE_NANOS) {
                Thread.sleep(1);
                long started = System.nanoTime();
                reply = routes.pollOrSubmit(1L, request, map.grid);
                long elapsed = System.nanoTime() - started;
                total += elapsed;
                worst = Math.max(worst, elapsed);
                calls++;
            }
            String status = reply.status() == PathRequestStatus.PENDING ? "PENDING_TIMEOUT"
                    : reply.proof() == null ? reply.status().name() : reply.proof().status().name();
            row(csv, axis, map, variant, start, "async-poll", status, reply.proof(),
                    calls, total, worst, System.nanoTime() - wallStart, 0, 0);
            if (reply.proof() != null) {
                var proof = reply.proof();
                total = 0;
                worst = 0;
                int reused = 0;
                wallStart = System.nanoTime();
                for (int i = 0; i < 32; i++) {
                    long started = System.nanoTime();
                    var repeated = routes.pollOrSubmit(1L, request, map.grid);
                    long elapsed = System.nanoTime() - started;
                    total += elapsed;
                    worst = Math.max(worst, elapsed);
                    if (repeated.proof() == proof) reused++;
                }
                // Counters identify the retained proof, not new work on each poll.
                row(csv, axis, map, variant, start, "same-candidate-reuse", proof.status().name(),
                        proof, 32, total, worst, System.nanoTime() - wallStart, reused, 0);
            }
        }
    }

    private static ClearanceRoutePlanner.Point legalStart(NavigationGrid grid, float x, float y, float radius) {
        ClearanceRoutePlanner.Point closest = null;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (int hy = Math.max(0, (int) (2 * y) - 16); hy <= Math.min(2 * HEIGHT, (int) (2 * y) + 16); hy++) {
            for (int hx = Math.max(0, (int) (2 * x) - 16); hx <= Math.min(2 * WIDTH, (int) (2 * x) + 16); hx++) {
                float px = hx * .5f, py = hy * .5f;
                float distance = (px - x) * (px - x) + (py - y) * (py - y);
                if (distance <= 64f && distance < bestDistance
                        && ManualTerrainMotion.canStand(grid, px, py, radius)) {
                    closest = new ClearanceRoutePlanner.Point(px, py);
                    bestDistance = distance;
                }
            }
        }
        return closest;
    }

    private static void row(StringBuilder csv, TraversalAxis axis, MapResult map, MechVariant variant,
                            ClearanceRoutePlanner.Point start, String label, String status,
                            ClearanceRoutePlanner.Result proof, int calls, long total, long worst,
                            long latency, int reused, int budget) {
        row(csv, axis, map, variant, start, label, status,
                proof == null ? -1 : proof.expandedNodes(), proof == null ? -1 : proof.clearanceChecks(),
                calls, total, worst, latency, reused, budget);
    }

    private static void row(StringBuilder csv, TraversalAxis axis, MapResult map, MechVariant variant,
                            ClearanceRoutePlanner.Point start, String label, String status,
                            long expanded, long checks, int calls, long total, long worst,
                            long latency, int reused, int budget) {
        csv.append(String.format(Locale.ROOT,
                "%s,%d,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%s,%s,%d,%d,%d,%.3f,%.3f,%.3f,%d,%d%n",
                axis, GENERATED_SEED, variant == null ? "all" : variant.id,
                variant == null ? Float.NaN : variant.radius, map.marineSpawnX + .5f, map.marineSpawnY + .5f,
                start == null ? Float.NaN : start.x(), start == null ? Float.NaN : start.y(),
                map.defenderSpawnX + .5f, map.defenderSpawnY + .5f, label, status,
                expanded, checks,
                calls, total / 1e6, worst / 1e6, latency / 1e6, reused, budget));
    }

    private static void measureSliced(StringBuilder csv, String label,
                                      ClearanceRoutePlanner planner, NavigationGrid grid) {
        final int quantum = 256;
        int steps = 0, previousExpanded = 0, worstExpanded = 0;
        long total = 0, worst = 0;
        try (var search = planner.begin(grid, 260.5f, 8.5f, 300.5f, 8.5f,
                .6f, .75f, 1_000_000)) {
            while (search.result().status() == ClearanceRoutePlanner.Status.PENDING) {
                long started = System.nanoTime();
                var result = search.step(quantum);
                long elapsed = System.nanoTime() - started;
                total += elapsed;
                worst = Math.max(worst, elapsed);
                int expanded = result.expandedNodes() - previousExpanded;
                if (expanded > quantum) throw new AssertionError("Search exceeded its step budget");
                previousExpanded = result.expandedNodes();
                worstExpanded = Math.max(worstExpanded, expanded);
                steps++;
            }
            csv.append(String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%.3f,%.3f%n",
                    label, search.result().status(), steps, previousExpanded, worstExpanded,
                    total / 1e6, worst / 1e6));
        }
    }

    private static void measure(StringBuilder csv, String label, ClearanceRoutePlanner planner,
                                NavigationGrid grid, float sx, float sy, float tx, float ty,
                                int queries) {
        long elapsed = 0, worst = 0, expanded = 0, checks = 0;
        int found = 0, limited = 0;
        for (int i = 0; i < queries; i++) {
            long start = System.nanoTime();
            var result = planner.findRoute(grid, sx, sy, tx, ty, .6f, .75f, BUDGET);
            long nanos = System.nanoTime() - start;
            elapsed += nanos;
            worst = Math.max(worst, nanos);
            expanded += result.expandedNodes();
            checks += result.clearanceChecks();
            if (result.status() == ClearanceRoutePlanner.Status.FOUND) found++;
            if (result.status() == ClearanceRoutePlanner.Status.SEARCH_LIMIT) limited++;
            if (result.expandedNodes() > BUDGET) throw new AssertionError("Search exceeded its expansion budget");
        }
        csv.append(String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%d,%.3f,%.3f%n",
                label, queries, found, limited, expanded, checks, elapsed / 1e6, worst / 1e6));
    }

    private static NavigationGrid floor() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
