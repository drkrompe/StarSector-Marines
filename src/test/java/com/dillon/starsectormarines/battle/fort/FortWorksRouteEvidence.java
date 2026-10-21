package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.ClearanceRoutePlanner;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.AssaultApproach;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortRoom;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
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
import java.util.Random;

/**
 * How far a fort's own garrison walks from inside its works to each stretch of its belt, against the straight line, for
 * a rifleman and for the fort side's mechs. Map evidence about stamped forts, not battle evidence: each fort kind is
 * stamped on open ground through the production {@link FortStage} and stood up as a simulation, so every structure the
 * grid closes is closed. Writes {@code build/reports/garrison/fort-routes.md}.
 *
 * <p>The start is the cell the defender's flow stages a fort's pool at (the fort aspect core with the least y, then the
 * least x, as {@code ConquestDefenderCommand.heldWorks} picks it), moved to the nearest walkable cell. Each stretch's
 * target is its member cell nearest its centre, the cell a manning order walks toward.
 */
@Tag("garrison-evidence")
class FortWorksRouteEvidence {

    private static final int FIELD_W = 200;
    private static final int FIELD_H = 170;
    /** A mech route's expansion budget: generous, so a failure is the ground's and not the budget's. */
    private static final int MECH_BUDGET = 400_000;
    private static final float MECH_TOLERANCE = 0.75f;

    @Test
    void inventoriesRoutesOutOfTheWorks() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        InstalledHullSpecs.install();
        InstalledVanillaData.install();

        StringBuilder out = new StringBuilder("# Fort works routes\n\n");
        out.append("From the fort pool's staging cell inside the works to each belt stretch's centre post. ")
                .append("Straight = straight line; Walk = rifleman path (8-connected, the grid's own step rule); ")
                .append("ratio = Walk / Straight. Mech columns: clearance route length for that chassis's radius, ")
                .append("or the planner's status when there is none.\n\n");
        for (FortKind kind : FortKind.values()) {
            for (boolean unbarred : new boolean[]{false, true}) {
                out.append("## ").append(kind).append(unbarred ? ", every sally port unbarred" : ", sally ports barred")
                        .append("\n\n");
                try (BattleSimulation sim = stamp(kind)) {
                    if (unbarred) {
                        sim.sallyDoors().openAllForEvidence(sim);
                    }
                    survey(sim, out);
                }
                out.append('\n');
            }
        }
        Path dir = root.resolve("build/reports/garrison");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("fort-routes.md"), out.toString());
        System.out.println(out);
    }

    private static BattleSimulation stamp(FortKind kind) {
        NavigationGrid grid = new NavigationGrid(FIELD_W, FIELD_H);
        CellTopology topology = new CellTopology(FIELD_W, FIELD_H);
        for (int y = 0; y < FIELD_H; y++) {
            for (int x = 0; x < FIELD_W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L), FIELD_W, FIELD_H, 7L);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, topology, null));
        ctx.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(topology, 7L));
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL.withDeclaredDefences(
                new GroundDefences(0, false).withForts(kind, 1)));
        FortStage stage = new FortStage();
        stage.run(ctx);
        if (stage.placed().size() != 1) throw new IllegalStateException(kind + " did not stand");
        return SceneBuilder.over(grid, topology).posts(List.copyOf(ctx.defensePosts))
                .doodads(List.copyOf(ctx.doodads)).missionCompletion(false).build().sim();
    }

    static void survey(BattleSimulation sim, StringBuilder out) {
        NavigationGrid grid = sim.getGrid();
        GarrisonableRegistry places = sim.garrisonables();
        for (Fort fort : sim.forts().forts()) {
            FortFrame frame = fort.frame();
            FortAspect staging = null;
            int[] core = null;
            for (FortAspect aspect : frame.plan().aspects()) {
                int[] c = frame.cell(frame.plan().coreCentre(aspect)[0], frame.plan().coreCentre(aspect)[1]);
                if (core == null || c[1] < core[1] || (c[1] == core[1] && c[0] < core[0])) {
                    core = c;
                    staging = aspect;
                }
            }
            if (staging == null) continue;
            int[] start = nearestWalkable(grid, core[0], core[1]);
            float[] walk = distances(grid, start[0], start[1]);
            out.append(String.format(Locale.ROOT, "Fort %d, staging at %s core, start (%d,%d)%n%n", fort.id(),
                    staging, start[0], start[1]));
            out.append("| stretch | where | centre | cells | straight | walk | ratio |");
            MechVariant[] mechs = {MechVariant.SIROCCO, MechVariant.BULWARK};
            for (MechVariant m : mechs) out.append(' ').append(m.id).append(" |");
            out.append('\n').append("|---|---|---|---|---|---|");
            for (int i = 0; i < mechs.length; i++) out.append("---|");
            out.append('\n');
            List<Float> ratios = new ArrayList<>();
            float worstWalk = 0;
            int[] mechReached = new int[mechs.length];
            int stretches = 0;
            for (int g = 0; g < places.count(); g++) {
                if (places.fortId(g) != fort.id()) continue;
                stretches++;
                int[] target = centrePost(places, g);
                float straight = (float) Math.hypot(target[0] - start[0], target[1] - start[1]);
                float w = walk[target[1] * grid.getWidth() + target[0]];
                float ratio = Float.isFinite(w) ? w / Math.max(1f, straight) : Float.POSITIVE_INFINITY;
                ratios.add(ratio);
                if (Float.isFinite(w)) worstWalk = Math.max(worstWalk, w);
                out.append(String.format(Locale.ROOT, "| %d | %s | (%d,%d) | %d | %.0f | %s | %s |", g,
                        frame.isWorks(target[0], target[1]) ? "works" : "belt", target[0], target[1],
                        places.size(g), straight, Float.isFinite(w) ? String.format(Locale.ROOT, "%.0f", w) : "none",
                        Float.isFinite(ratio) ? String.format(Locale.ROOT, "%.2f", ratio) : "-"));
                for (int i = 0; i < mechs.length; i++) {
                    int[] ms = standable(grid, start, mechs[i].radius);
                    int[] mt = standable(grid, target, mechs[i].radius);
                    ClearanceRoutePlanner.Result r = new ClearanceRoutePlanner().findRoute(grid, ms[0] + 0.5f,
                            ms[1] + 0.5f, mt[0] + 0.5f, mt[1] + 0.5f, mechs[i].radius, MECH_TOLERANCE,
                            MECH_BUDGET);
                    if (r.status() == ClearanceRoutePlanner.Status.FOUND) {
                        mechReached[i]++;
                        out.append(String.format(Locale.ROOT, " %.0f |", length(start, r)));
                    } else {
                        out.append(' ').append(r.status()).append(" |");
                    }
                }
                out.append('\n');
            }
            // Mechs from the works' yard: from inside each gate (its entry cell) and from the middle of the works, to the
            // open ground six cells out of every gate.
            FortPlan plan = frame.plan();
            List<int[]> yard = new ArrayList<>();
            List<String> yardNames = new ArrayList<>();
            for (int a = 0; a < plan.approaches().size(); a++) {
                AssaultApproach ap = plan.approaches().get(a);
                if (ap.variant() == AssaultApproach.Variant.BREACH) continue;
                yard.add(frame.cell(ap.entryX(), ap.entryY()));
                yardNames.add("inside gate " + ap.gateIndex());
            }
            int[] mid = middleOfWorks(plan);
            yard.add(frame.cell(mid[0], mid[1]));
            yardNames.add("works middle");
            out.append("\n| mech from | to |");
            for (MechVariant m : mechs) out.append(' ').append(m.id).append(" |");
            out.append("\n|---|---|");
            for (int i = 0; i < mechs.length; i++) out.append("---|");
            out.append('\n');
            for (int y = 0; y < yard.size(); y++) {
                for (FortPlan.Gate gate : plan.gates()) {
                    int[] outside = frame.cell(gate.mouthX() + 6 * gate.outX(), gate.mouthY() + 6 * gate.outY());
                    out.append(String.format(Locale.ROOT, "| %s (%d,%d) | out of gate at (%d,%d) |", yardNames.get(y),
                            yard.get(y)[0], yard.get(y)[1], outside[0], outside[1]));
                    for (MechVariant m : mechs) {
                        int[] ms = standable(grid, yard.get(y), m.radius);
                        int[] mt = standable(grid, outside, m.radius);
                        ClearanceRoutePlanner.Result r = new ClearanceRoutePlanner().findRoute(grid, ms[0] + 0.5f,
                                ms[1] + 0.5f, mt[0] + 0.5f, mt[1] + 0.5f, m.radius, MECH_TOLERANCE, MECH_BUDGET);
                        out.append(' ').append(r.status() == ClearanceRoutePlanner.Status.FOUND
                                ? String.format(Locale.ROOT, "%.0f", length(ms, r)) : r.status().name()).append(" |");
                    }
                    out.append('\n');
                }
            }
            // Every fourth works cell a mech can stand on: does it route out of the main gate at all?
            int[] mainOut = frame.cell(plan.gates().get(0).mouthX() + 6 * plan.gates().get(0).outX(),
                    plan.gates().get(0).mouthY() + 6 * plan.gates().get(0).outY());
            for (MechVariant m : mechs) {
                int standing = 0, routed = 0;
                List<String> stuck = new ArrayList<>();
                int[] mt = standable(grid, mainOut, m.radius);
                for (int lx = 0; lx < plan.width(); lx += 4) {
                    for (int ly = 0; ly < plan.height(); ly += 4) {
                        if (!plan.isWorks(lx, ly) || plan.isWall(lx, ly)) continue;
                        int[] c = frame.cell(lx, ly);
                        if (!ManualTerrainMotion.canStand(grid, c[0] + 0.5f, c[1] + 0.5f, m.radius)) continue;
                        standing++;
                        ClearanceRoutePlanner.Result r = new ClearanceRoutePlanner().findRoute(grid, c[0] + 0.5f,
                                c[1] + 0.5f, mt[0] + 0.5f, mt[1] + 0.5f, m.radius, MECH_TOLERANCE, MECH_BUDGET);
                        if (r.status() == ClearanceRoutePlanner.Status.FOUND) routed++;
                        else if (stuck.size() < 12) stuck.add("(" + c[0] + "," + c[1] + ")" + room(plan, lx, ly));
                    }
                }
                out.append(String.format(Locale.ROOT, "%n%s: of %d sampled works cells it can stand on, %d route out of the main gate; stuck at %s%n",
                        m.id, standing, routed, stuck));
            }
            float[] sorted = new float[ratios.size()];
            for (int i = 0; i < sorted.length; i++) sorted[i] = ratios.get(i);
            Arrays.sort(sorted);
            out.append(String.format(Locale.ROOT, "%nStretches %d; walk/straight median %.2f, worst %.2f; longest walk %.0f cells",
                    stretches, sorted.length == 0 ? 0f : sorted[sorted.length / 2],
                    sorted.length == 0 ? 0f : sorted[sorted.length - 1], worstWalk));
            for (int i = 0; i < mechs.length; i++) {
                out.append(String.format(Locale.ROOT, "; %s reaches %d of %d", mechs[i].id, mechReached[i], stretches));
            }
            out.append(".\n\n");
        }
    }

    /** " in <room art key>" when the local cell is on a room's floor, else "". */
    private static String room(FortPlan plan, int lx, int ly) {
        for (FortRoom room : plan.rooms()) {
            int[] f = room.floor();
            if (lx >= f[0] && ly >= f[1] && lx < f[0] + f[2] && ly < f[1] + f[3]) return " in " + room.aspect() + " room";
        }
        return "";
    }

    /** The works cell nearest the middle of the works' bounding box. */
    private static int[] middleOfWorks(FortPlan plan) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int x = 0; x < plan.width(); x++) {
            for (int y = 0; y < plan.height(); y++) {
                if (!plan.isWorks(x, y)) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return new int[]{(minX + maxX) / 2, (minY + maxY) / 2};
    }

    /** The nearest cell to {@code cell} whose centre a body of {@code radius} can stand on. */
    static int[] standable(NavigationGrid grid, int[] cell, float radius) {
        for (int r = 0; r < 8; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = cell[0] + dx;
                    int y = cell[1] + dy;
                    if (grid.inBounds(x, y) && ManualTerrainMotion.canStand(grid, x + 0.5f, y + 0.5f, radius)) {
                        return new int[]{x, y};
                    }
                }
            }
        }
        return cell;
    }

    private static float length(int[] start, ClearanceRoutePlanner.Result r) {
        float total = 0;
        float px = start[0] + 0.5f;
        float py = start[1] + 0.5f;
        for (ClearanceRoutePlanner.Point p : r.waypoints()) {
            total += (float) Math.hypot(p.x() - px, p.y() - py);
            px = p.x();
            py = p.y();
        }
        return total;
    }

    /** The stretch's member cell nearest its centre. */
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

    static int[] nearestWalkable(NavigationGrid grid, int x, int y) {
        for (int r = 0; r < 12; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (grid.inBounds(x + dx, y + dy) && grid.isWalkable(x + dx, y + dy)) return new int[]{x + dx, y + dy};
                }
            }
        }
        return new int[]{x, y};
    }

    /** Rifleman walking distance from one cell to every cell, by the grid's own step rule. */
    static float[] distances(NavigationGrid grid, int sx, int sy) {
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
            if (n[0] > dist[y * w + x]) continue;
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
