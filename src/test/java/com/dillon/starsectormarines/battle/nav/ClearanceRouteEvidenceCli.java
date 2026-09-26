package com.dillon.starsectormarines.battle.nav;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Opt-in route-cost evidence; synthetic terrain isolates the planner from battle setup. */
public final class ClearanceRouteEvidenceCli {
    private static final int WIDTH = 560;
    private static final int HEIGHT = 336;
    private static final int BUDGET = 32768;

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
        measure(csv, "open-long", planner, floor, 4.5f, 4.5f, 550.5f, 325.5f, 1);
        for (int y = 0; y < HEIGHT - 12; y++) floor.setWalkable(280, y, false);
        measure(csv, "long-detour", planner, floor, 260.5f, 8.5f, 300.5f, 8.5f, 1);
        for (int y = HEIGHT - 12; y < HEIGHT; y++) floor.setWalkable(280, y, false);
        measure(csv, "sealed-region", planner, floor, 260.5f, 8.5f, 300.5f, 8.5f, 1);
        measure(csv, "repeated-local-probes", planner, floor, 30.5f, 30.5f, 65.5f, 50.5f, 32);
        String report = csv.toString();
        Files.writeString(output.resolve("summary.csv"), report, StandardCharsets.UTF_8);
        System.out.print(report);
        System.out.println("Timings are host-local diagnostics. Terrain is synthetic at Conquest dimensions; this is not a battle performance claim.");
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
