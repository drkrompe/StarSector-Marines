package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.ClearanceRoutePlanner;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sally.SallyDoorSurvey;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.function.Supplier;

/**
 * How far a place's holder walks from inside it to the ground it owns, against the straight line, on several missions'
 * production maps at tick zero: for every tactical node, from its anchor (the cell its garrison stands to from) to each
 * garrisonable stretch on its compound ground or within {@link #GROUND_MARGIN} cells of it, and to the open ground just
 * beyond each face of the compound. A rifleman walks by the grid's own step rule; a mech's reach is the clearance route
 * planner's for its radius. Read twice, once as generated and once with every sally door the map offers opened (see
 * {@code garrisonables.md}, sally doors), so a door's worth is one column against the next. Map evidence, not battle
 * evidence. Writes {@code build/reports/garrison/holder-routes.md}.
 */
@Tag("garrison-evidence")
class HolderRouteEvidence {

    private static final int GROUND_MARGIN = 7;
    /** How far beyond a compound face its "outside" sample stands. */
    private static final int FACE_STEP = 4;
    /** A walk this many cells longer than the straight line is a detour worth naming. */
    private static final float DETOUR_CELLS = 20f;
    private static final float WALK_LIMIT = 700f;
    private static final int MECH_BUDGET = 200_000;
    private static final float MECH_TOLERANCE = 0.75f;
    private static final MechVariant[] MECHS = {MechVariant.SIROCCO, MechVariant.BULWARK};

    private record Case(String name, Supplier<BattleSimulation> build) {}

    /** One route's reading: straight line, rifleman walk (infinite: none), and per mech: 0 no cell to start from, 1 no route, 2 a route. */
    private record Leg(String what, float straight, float walk, int[] mech) {
        float excess() { return Float.isFinite(walk) ? walk - straight : Float.POSITIVE_INFINITY; }
    }

    @Test
    void inventoriesHolderRoutes() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        InstalledHullSpecs.install();
        InstalledVanillaData.install();

        List<ShuttleAssignment> lift = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        List<ShuttleAssignment> opening = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        List<Case> cases = new ArrayList<>();
        cases.add(new Case("CONQUEST", () -> BattleSetup.createConquest(91L,
                List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1)), false, OperationTier.FIRST_CONTRACT,
                RiskLevel.LOW, TargetProfile.NEUTRAL)));
        for (DebugMissionCatalog.Scenario scenario : new DebugMissionCatalog.Scenario[]{
                DebugMissionCatalog.Scenario.RELIEF, DebugMissionCatalog.Scenario.COUNTERATTACK,
                DebugMissionCatalog.Scenario.JUNCTION, DebugMissionCatalog.Scenario.RELAY_NINE,
                DebugMissionCatalog.Scenario.DUST_LEDGER, DebugMissionCatalog.Scenario.SALVAGE_RIGHTS,
                DebugMissionCatalog.Scenario.COLD_START}) {
            boolean first = scenario == DebugMissionCatalog.Scenario.RELIEF
                    || scenario == DebugMissionCatalog.Scenario.COUNTERATTACK;
            cases.add(new Case(scenario.name(), () -> DebugMissionCatalog.build(scenario,
                    scenario.mission().battleSeed, first ? opening : lift)));
        }

        StringBuilder out = new StringBuilder("# Holder routes\n\n");
        out.append("Per tactical node, from its anchor to each owned stretch (centre post of a garrisonable on its compound ")
                .append("ground or within ").append(GROUND_MARGIN).append(" cells) and to open ground ").append(FACE_STEP)
                .append(" cells beyond each compound face. Excess = rifleman walk - straight line, cells; a leg with ")
                .append("excess over ").append((int) DETOUR_CELLS).append(" is a detour. Mechs: routes found, of the legs a ")
                .append("chassis could stand at both ends of (it starts from the nearest cell inside the place it can stand on). 'doors' = with every sally door ")
                .append("the map offers opened.\n\n");
        StringBuilder table = new StringBuilder("| mission | nodes | legs | detours stretch / face | worst excess | mean excess | ")
                .append("Sirocco routes | Bulwark routes | doors | detours, doors | worst, doors | mean, doors | ")
                .append("Sirocco, doors | Bulwark, doors |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Case c : cases) {
            out.append("## ").append(c.name).append("\n\n");
            try (BattleSimulation sim = c.build.get()) {
                List<List<Leg>> before = survey(sim);
                int offered = sim.sallyDoors().openAllForEvidence(sim);
                List<List<Leg>> after = survey(sim);
                write(sim, before, after, out);
                table.append("| ").append(c.name).append(" | ").append(before.size()).append(" | ")
                        .append(summary(before)).append(" | ").append(offered).append(" | ")
                        .append(summary(after).replaceFirst("^\\d+ \\| ", "")).append(" |\n");
            } catch (RuntimeException | Error e) {
                out.append("BUILD FAILED: ").append(e).append("\n\n");
                table.append("| ").append(c.name).append(" | failed |\n");
            }
        }
        String report = "# Holder routes\n\n" + table + "\n" + out.substring("# Holder routes\n\n".length());
        Path dir = root.resolve("build/reports/garrison");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("holder-routes.md"), report);
        System.out.println(table);
    }

    /** "legs | detours | worst | mean | sirocco | bulwark" over every node's legs. */
    private static String summary(List<List<Leg>> nodes) {
        int legs = 0, stretchDetours = 0, faceDetours = 0;
        float worst = 0, sum = 0;
        int reached = 0;
        int[] mech = new int[MECHS.length];
        int[] tried = new int[MECHS.length];
        for (List<Leg> node : nodes) {
            for (Leg leg : node) {
                legs++;
                if (leg.excess() > DETOUR_CELLS) {
                    if (leg.what.startsWith("stretch")) stretchDetours++;
                    else faceDetours++;
                }
                if (Float.isFinite(leg.walk)) {
                    worst = Math.max(worst, leg.excess());
                    sum += leg.excess();
                    reached++;
                }
                for (int i = 0; i < MECHS.length; i++) {
                    if (leg.mech[i] > 0) tried[i]++;
                    if (leg.mech[i] == 2) mech[i]++;
                }
            }
        }
        return String.format(Locale.ROOT, "%d | %d / %d | %.0f | %.1f | %d/%d | %d/%d", legs, stretchDetours, faceDetours,
                worst, reached == 0 ? 0f : sum / reached, mech[0], tried[0], mech[1], tried[1]);
    }

    private static void write(BattleSimulation sim, List<List<Leg>> before, List<List<Leg>> after, StringBuilder out) {
        List<TacticalNode> nodes = nodes(sim);
        out.append("| node | kind | footprint | walled (bldg / cmpd) | legs | detours | worst leg | excess | doors: excess |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
        for (int n = 0; n < nodes.size(); n++) {
            TacticalNode node = nodes.get(n);
            List<Leg> legs = before.get(n);
            Leg worst = null;
            int detours = 0;
            for (Leg leg : legs) {
                if (leg.excess() > DETOUR_CELLS) detours++;
                if (worst == null || leg.excess() > worst.excess()) worst = leg;
            }
            if (worst == null) continue;
            Leg same = null;
            for (Leg leg : after.get(n)) if (leg.what.equals(worst.what)) same = leg;
            float bldg = SallyDoorSurvey.enclosedShare(sim.getGrid(), sim.getTopology(), node.left, node.top, node.right, node.bottom);
            float cmpd = SallyDoorSurvey.enclosedShare(sim.getGrid(), sim.getTopology(), node.compoundLeft(),
                    node.compoundTop(), node.compoundRight(), node.compoundBottom());
            out.append(String.format(Locale.ROOT, "| %d | %s | %dx%d | %.2f / %.2f | %d | %d | %s | %s | %s |%n", n, node.kind,
                    node.compoundRight() - node.compoundLeft() + 1, node.compoundBottom() - node.compoundTop() + 1, bldg, cmpd,
                    legs.size(), detours, worst.what, excess(worst), same == null ? "-" : excess(same)));
        }
        out.append('\n');
    }

    private static String excess(Leg leg) {
        if (!Float.isFinite(leg.walk)) return "none";
        StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "%.0f (%.0f/%.0f)", leg.excess(), leg.walk, leg.straight));
        for (int i = 0; i < MECHS.length; i++) if (leg.mech[i] == 1) s.append(' ').append(MECHS[i].id).append(" no");
        return s.toString();
    }

    private static List<TacticalNode> nodes(BattleSimulation sim) {
        return sim.getTacticalMap() == null ? List.of() : sim.getTacticalMap().all();
    }

    private static List<List<Leg>> survey(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        GarrisonableRegistry places = sim.garrisonables();
        List<List<Leg>> result = new ArrayList<>();
        for (TacticalNode node : nodes(sim)) {
            List<Leg> legs = new ArrayList<>();
            result.add(legs);
            int[] start = nearestWalkable(grid, node.anchorX, node.anchorY, 10);
            if (start == null) continue;
            float[] walk = distances(grid, start[0], start[1]);
            int[][] mechStart = new int[MECHS.length][];
            for (int i = 0; i < MECHS.length; i++) mechStart[i] = standableWithin(grid, start, MECHS[i].radius, node);
            List<int[]> targets = new ArrayList<>();
            List<String> names = new ArrayList<>();
            if (places != null) {
                for (int g = 0; g < places.count(); g++) {
                    if (places.size(g) == 0) continue;
                    int cx = places.centreX(g), cy = places.centreY(g);
                    if (cx < node.compoundLeft() - GROUND_MARGIN || cx > node.compoundRight() + GROUND_MARGIN
                            || cy < node.compoundTop() - GROUND_MARGIN || cy > node.compoundBottom() + GROUND_MARGIN) continue;
                    targets.add(centrePost(places, g));
                    names.add("stretch " + g);
                }
            }
            int midX = (node.compoundLeft() + node.compoundRight()) / 2;
            int midY = (node.compoundTop() + node.compoundBottom()) / 2;
            int[][] faces = {{midX, node.compoundTop() - FACE_STEP}, {midX, node.compoundBottom() + FACE_STEP},
                    {node.compoundLeft() - FACE_STEP, midY}, {node.compoundRight() + FACE_STEP, midY}};
            String[] faceNames = {"north face", "south face", "west face", "east face"};
            int[][] outward = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
            for (int f = 0; f < faces.length; f++) {
                if (!grid.inBounds(faces[f][0], faces[f][1])) continue;
                int[] outside = bestOutside(grid, walk, faces[f], outward[f]);
                if (outside == null) continue;
                targets.add(outside);
                names.add(faceNames[f]);
            }
            for (int t = 0; t < targets.size(); t++) {
                int[] target = targets.get(t);
                float straight = (float) Math.hypot(target[0] - start[0], target[1] - start[1]);
                float w = walk[target[1] * grid.getWidth() + target[0]];
                int[] mech = new int[MECHS.length];
                for (int i = 0; i < MECHS.length; i++) {
                    if (mechStart[i] == null) continue;
                    int[] to = standable(grid, target, MECHS[i].radius);
                    if (to == null) continue;
                    ClearanceRoutePlanner.Result r = new ClearanceRoutePlanner().findRoute(grid, mechStart[i][0] + 0.5f,
                            mechStart[i][1] + 0.5f, to[0] + 0.5f, to[1] + 0.5f, MECHS[i].radius, MECH_TOLERANCE,
                            MECH_BUDGET);
                    mech[i] = r.status() == ClearanceRoutePlanner.Status.FOUND ? 2 : 1;
                }
                legs.add(new Leg(names.get(t), straight, w, mech));
            }
        }
        return result;
    }

    /** The cell nearest {@code cell}, inside the node's compound, a body of {@code radius} can stand on; or null. */
    private static int[] standableWithin(NavigationGrid grid, int[] cell, float radius, TacticalNode node) {
        for (int r = 0; r < 12; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = cell[0] + dx, y = cell[1] + dy;
                    if (x < node.compoundLeft() || x > node.compoundRight() || y < node.compoundTop() || y > node.compoundBottom()) continue;
                    if (grid.inBounds(x, y) && ManualTerrainMotion.canStand(grid, x + 0.5f, y + 0.5f, radius)) return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static int[] standable(NavigationGrid grid, int[] cell, float radius) {
        for (int r = 0; r < 4; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = cell[0] + dx, y = cell[1] + dy;
                    if (grid.inBounds(x, y) && ManualTerrainMotion.canStand(grid, x + 0.5f, y + 0.5f, radius)) return new int[]{x, y};
                }
            }
        }
        return null;
    }

    /**
     * The ground just beyond a face: of the open cells from one step short of the sample to two beyond it, and three
     * either side along the face, the one the rifleman walks to soonest (the nearest if none is reached). A face sample
     * that lands behind somebody else's wall would otherwise measure that wall.
     */
    private static int[] bestOutside(NavigationGrid grid, float[] walk, int[] sample, int[] out) {
        int[] best = null;
        float bestWalk = Float.POSITIVE_INFINITY;
        int ax = out[1] != 0 ? 1 : 0, ay = out[0] != 0 ? 1 : 0;
        for (int d = -1; d <= 2; d++) {
            for (int a = -3; a <= 3; a++) {
                int x = sample[0] + out[0] * d + ax * a, y = sample[1] + out[1] * d + ay * a;
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                float w = walk[y * grid.getWidth() + x];
                if (best == null || w < bestWalk) {
                    best = new int[]{x, y};
                    bestWalk = w;
                }
            }
        }
        return best;
    }

    private static int[] centrePost(GarrisonableRegistry places, int g) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < places.size(g); i++) {
            long dx = places.cellX(g, i) - places.centreX(g);
            long dy = places.cellY(g, i) - places.centreY(g);
            if (dx * dx + dy * dy < bestD) {
                bestD = dx * dx + dy * dy;
                best = i;
            }
        }
        return new int[]{places.cellX(g, best), places.cellY(g, best)};
    }

    private static int[] nearestWalkable(NavigationGrid grid, int x, int y, int reach) {
        for (int r = 0; r <= reach; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (grid.inBounds(x + dx, y + dy) && grid.isWalkable(x + dx, y + dy)) return new int[]{x + dx, y + dy};
                }
            }
        }
        return null;
    }

    /** Rifleman walking distance from one cell, by the grid's own step rule, out to {@link #WALK_LIMIT}. */
    private static float[] distances(NavigationGrid grid, int sx, int sy) {
        int w = grid.getWidth();
        float[] dist = new float[w * grid.getHeight()];
        Arrays.fill(dist, Float.POSITIVE_INFINITY);
        dist[sy * w + sx] = 0;
        PriorityQueue<float[]> open = new PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));
        open.add(new float[]{0, sx, sy});
        while (!open.isEmpty()) {
            float[] n = open.poll();
            int x = (int) n[1];
            int y = (int) n[2];
            if (n[0] > dist[y * w + x] || n[0] > WALK_LIMIT) continue;
            for (Direction d : Direction.ALL) {
                if (!grid.canTraverseCellStep(x, y, d)) continue;
                float nd = n[0] + (d.isDiagonal() ? 1.4142f : 1f);
                int idx = (y + d.dy) * w + x + d.dx;
                if (nd < dist[idx]) {
                    dist[idx] = nd;
                    open.add(new float[]{nd, x + d.dx, y + d.dy});
                }
            }
        }
        return dist;
    }
}
