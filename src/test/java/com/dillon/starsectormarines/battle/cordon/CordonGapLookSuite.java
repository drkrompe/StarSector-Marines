package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The gap law as a question for the owner ({@code cordon-map-intent.md}, Redoubts where roads cross;
 * {@link CordonGapLaw}): on a real pooled Cordon map whose line breaks the law, the worst neighbouring pair of
 * redoubts is drawn through the look preset as it is, and again with each way of closing it composed in the scene
 * only: more picket nests along the course ({@code nests}), the redoubt moved nearer its neighbour ({@code closer}),
 * every gun on the line a longer-reaching emplacement already in the mod's catalog ({@code longer-guns}), and the line
 * left as it is ({@code leave}). A swarm pack comes at the gap in each, so the stills show what the guns do to it.
 *
 * <p>Coverage is invisible in a plain picture, so each gun on the line is drawn with one faint ring of the ground its
 * fire lands on, its range plus its spread and splash: two rings that touch are two guns that close the gap between
 * them, which is the law. The rings are drawn the same in every option, and nothing else is annotated.
 *
 * <p>Writes {@code where.png} (the whole pair) and {@code <option>.png}, framed on the broken step. Map edits live in
 * this suite, never in map generation. {@code -Dsnapshot.cordonGap.variant} ({@code line}), {@code .density}
 * ({@code urban}), {@code .seed} (6), {@code .pair} (the widest broken one, as its western redoubt's number) and
 * {@code .options} narrow it; {@code .survey=urban:1,6,11;town:8,10} only prints those maps' gaps. Opt-in: a design
 * question, not part of {@code all}.
 */
public final class CordonGapLookSuite implements SnapshotSuite {

    static final int W = LookReview.WIDTH, H = LookReview.HEIGHT;
    /** Ticks a still is chosen from, and the first tick considered. */
    static final int TICKS = 480, EVERY = 6, FIRST_STILL = 60;
    /** Cells in front of the line the pack starts, and behind it the pack is sent. */
    static final int PACK_OUT = 26, PACK_THROUGH = 34;
    /** The longer gun: the heaviest direct-fire emplacement the catalog already has. */
    static final String LONGER = TurretCatalogRegistry.HEAVY_MORTAR_STRUCTURE_ID;
    private static final String MORTAR_MOUNT = "mount.turret-heavy-mortar";
    private static final String ISSUER = "cordon-gap-look";

    private static final Color FILL = new Color(255, 186, 72, 40);
    private static final Color EDGE = new Color(255, 208, 120, 175);
    private static final Color PIP = new Color(255, 228, 160, 230);

    @Override public String id() { return "cordon-gap-look"; }

    @Override public String label() { return "Cordon: the gap law, and four ways to answer it, on a real broken pair"; }

    @Override public boolean inAll() { return false; }

    /** The gun's reach as the law reads it: range, then its spread and splash beyond. */
    record Gun(double range, double spread, double splash) {
        double covers() { return range + spread + splash; }
        double limit() { return 2 * covers(); }
    }

    /** The broken pair as measured on today's map: its chain of guns and the widest step on it. */
    record Pair(int west, int east, List<int[]> chain, int widestFrom, double widest) {
        int[] a() { return chain.get(widestFrom); }
        int[] b() { return chain.get(widestFrom + 1); }
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        DiskRegistries.install(context.projectRoot());
        String survey = System.getProperty("snapshot.cordonGap.survey", "").trim();
        CordonVariant variant = CordonVariant.valueOf(
                System.getProperty("snapshot.cordonGap.variant", "line").trim().toUpperCase(Locale.ROOT));
        if (!survey.isEmpty()) {
            survey(variant, survey);
            return List.of();
        }
        CordonPlan.Density density = CordonPlan.Density.valueOf(
                System.getProperty("snapshot.cordonGap.density", "urban").trim().toUpperCase(Locale.ROOT));
        long seed = Long.parseLong(System.getProperty("snapshot.cordonGap.seed", "6").trim());
        String wanted = System.getProperty("snapshot.cordonGap.options", "all").trim();
        String pairWanted = System.getProperty("snapshot.cordonGap.pair", "").trim();

        CordonSetup.CordonMap today = map(variant, density, seed);
        Pair pair = pair(today, pairWanted.isEmpty() ? -1 : Integer.parseInt(pairWanted));
        int[] a = pair.a(), b = pair.b();
        float midX = (a[0] + b[0]) / 2f + 0.5f, midY = (a[1] + b[1]) / 2f + 0.5f;
        float optionCells = (float) Math.max(BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, pair.widest() + 40);
        System.out.printf(Locale.ROOT, "    [cordon-gap-look] %s %s seed %d: pair %d-%d widest step %.1f from %d,%d to %d,%d"
                        + " (law %.1f); option frame %.0f cells across%n", variant, density, seed, pair.west(),
                pair.east(), pair.widest(), a[0], a[1], b[0], b[1], CordonGapLaw.LIMIT, optionCells);

        Gun line = new Gun(CordonGapLaw.REACH, CordonGapLaw.HIT_SPREAD, CordonGapLaw.SPLASH);
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String option : List.of("where", "leave", "nests", "closer", "longer-guns")) {
            if (!wanted.equals("all") && !List.of(wanted.split("\\s*,\\s*")).contains(option)) continue;
            // A fresh map each: standing a battle up seats guns into the map's own grid.
            CordonSetup.CordonMap cordon = map(variant, density, seed);
            CordonStructureWorks works = null;
            List<int[]> extraNests = new ArrayList<>();
            Gun gun = line;
            float fx = midX, fy = midY, cells = optionCells;
            switch (option) {
                case "where" -> {
                    float[] span = pairSpan(cordon, pair);
                    fx = span[0];
                    fy = span[1];
                    cells = Math.max(optionCells, span[2]);
                }
                case "nests" -> {
                    works = new CordonStructureWorks(cordon.map(), seed);
                    extraNests.addAll(nests(cordon, pair, works, line));
                    works.finish();
                }
                case "closer" -> {
                    works = new CordonStructureWorks(cordon.map(), seed);
                    cordon = closer(cordon, pair, works, line);
                    works.finish();
                }
                case "longer-guns" -> gun = longerGun();
                default -> { }
            }
            out.add(still(context, cordon, variant, option, works, extraNests, gun, pair, fx, fy, cells));
        }
        return out;
    }

    // ---------------------------------------------------------------- the scene

    private SnapshotArtifact still(SnapshotContext context, CordonSetup.CordonMap cordon, CordonVariant variant,
                                   String option, CordonStructureWorks works, List<int[]> extraNests, Gun gun,
                                   Pair pair, float fx, float fy, float cells) throws Exception {
        // Read off the map before the battle stands up: seating the guns closes their cells, which moves the towers'
        // reading.
        List<int[]> guns = lineGuns(cordon);
        guns.addAll(extraNests);
        TurretCatalogRegistry before = TurretCatalogRegistry.installed();
        if (option.equals("longer-guns")) TurretCatalogRegistry.install(swapped(context.modRoot()));
        try (BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(), CordonSequence.Timetable.STANDARD)) {
            if (works != null) sim.setBuildings(works.buildings());
            int k = 0;
            for (int[] nest : extraNests) ColonyMilitia.seatEmplacement(sim, nest, "Picket gap nest " + (++k));
            int front = CordonFront.frontward(cordon.plan());
            int[] a = pair.a(), b = pair.b();
            int mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2;
            List<Long> packIds = pack(sim, mx, my + front * PACK_OUT, mx, my - front * PACK_THROUGH);
            for (CordonRedoubt r : sim.getCordon().redoubts()) {
                for (long id : r.guns()) {
                    System.out.printf(Locale.ROOT, "    [cordon-gap-look/%s] redoubt %d gun at %d,%d: %s, range %.0f%n",
                            option, r.number(), sim.world().cellX(id), sim.world().cellY(id),
                            sim.turretState().weapon(id).id, sim.world().attackRange(id));
                }
            }
            ReviewImpactFx effects = new ReviewImpactFx(context.modRoot());
            BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), W, H, fx, fy, cells);
            BufferedImage best = null, nearest = null;
            int bestPainted = 0, bestTick = -1, nearestTick = -1, fired = 0;
            double nearestDistance = Double.MAX_VALUE;
            for (int tick = 1; tick <= TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                effects.feed(sim);
                fired += sim.getShotsThisFrame().size();
                if (tick < FIRST_STILL || tick % EVERY != 0) continue;
                BufferedImage frame = renderer.render(sim, "", ReviewAnnotations.NONE, effects);
                int painted = effects.lastPainted();
                if (painted > bestPainted) {
                    bestPainted = painted;
                    bestTick = tick;
                    best = frame;
                }
                double d = packDistance(sim, packIds, mx, my);
                if (d < nearestDistance) {
                    nearestDistance = d;
                    nearestTick = tick;
                    nearest = frame;
                }
            }
            // A still with fire in it where there is fire; where no gun reaches, the pack in the gap.
            BufferedImage still = best != null && !option.equals("where") ? best : nearest;
            System.out.printf(Locale.ROOT, "    [cordon-gap-look/%s] %d rounds fired; still at tick %d (%d particles;"
                            + " pack nearest the gap at tick %d), %d line guns, ring %.1f cells%n", option, fired,
                    still == best ? bestTick : nearestTick, bestPainted, nearestTick, guns.size(), gun.covers());
            rings(still, sim.getGrid().getWidth(), sim.getGrid().getHeight(), fx, fy, cells, guns, gun);
            return new SnapshotArtifact(option + ".png", still);
        } finally {
            TurretCatalogRegistry.install(before);
        }
    }

    /** How far the pack's living middle stands from the gap's middle; far when none is left. */
    private static double packDistance(BattleSimulation sim, List<Long> pack, int mx, int my) {
        double sx = 0, sy = 0;
        int n = 0;
        for (long id : pack) {
            if (!sim.world().isAlive(id)) continue;
            sx += sim.world().x(id);
            sy += sim.world().y(id);
            n++;
        }
        if (n == 0) return Double.MAX_VALUE;
        return Math.hypot(sx / n - mx, sy / n - my);
    }

    /** A swarm pack at the gap: twenty runners and three brutes attack-moving through it. */
    static List<Long> pack(BattleSimulation sim, int x, int y, int towardX, int towardY) {
        List<Long> ids = new ArrayList<>();
        swarm(sim, ids, UnitType.SWARM_RUNNER, 20, x, y, towardX, towardY);
        swarm(sim, ids, UnitType.SWARM_BRUTE, 3, x + 5, y, towardX, towardY);
        return ids;
    }

    private static void swarm(BattleSimulation sim, List<Long> ids, UnitType type, int count, int x, int y,
                              int towardX, int towardY) {
        List<int[]> cells = CordonGround.clusterNear(sim, x, y, count);
        int squad = sim.mintSquad(Faction.DEFENDER, type);
        if (cells.isEmpty()) return;
        for (int i = 0; i < cells.size(); i++) {
            long id = sim.spawn(new EntitySpec("gap pack " + type.name().toLowerCase(Locale.ROOT) + " " + i,
                    Faction.DEFENDER, type, cells.get(i)[0], cells.get(i)[1]).squad(squad));
            ids.add(id);
            if (sim.getSquad(squad).leaderId == 0L) sim.getSquad(squad).leaderId = id;
        }
        sim.getSquad(squad).originalSize = cells.size();
        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, towardX, towardY),
                CommandAuthority.SCRIPTED, ISSUER, "gap pack");
    }

    /** One faint ring per gun of the ground its fire lands on, the union filled once so overlaps do not darken. */
    static void rings(BufferedImage image, int gridW, int gridH, float fx, float fy, float cells, List<int[]> guns,
                      Gun gun) {
        BattleCamera camera = new BattleCamera(gridW, gridH);
        float cellPx = (float) Math.floor(image.getWidth() / cells);
        camera.setViewport(0f, 0f, image.getWidth(), image.getHeight(), cellPx);
        camera.centerOn(fx, fy);
        double r = gun.covers() * cellPx;
        Area union = new Area();
        List<double[]> centres = new ArrayList<>();
        for (int[] g : guns) {
            double sx = camera.cellToScreenX(g[0] + 0.5f);
            double sy = image.getHeight() - camera.cellToScreenY(g[1] + 0.5f);
            centres.add(new double[]{sx, sy});
            union.add(new Area(new Ellipse2D.Double(sx - r, sy - r, 2 * r, 2 * r)));
        }
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(FILL);
        g.fill(union);
        g.setColor(EDGE);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(union);
        g.setColor(PIP);
        g.setStroke(new BasicStroke(2f));
        double pip = Math.max(6, cellPx * 0.9);
        for (double[] c : centres) g.draw(new Ellipse2D.Double(c[0] - pip, c[1] - pip, 2 * pip, 2 * pip));
        g.dispose();
    }

    // ---------------------------------------------------------------- the options

    /**
     * More picket nests: each step of the pair's chain wider than the law gets as many sandbagged gun nests, evenly
     * along it, as bring every step inside it, each on the open ground nearest its mark.
     */
    static List<int[]> nests(CordonSetup.CordonMap cordon, Pair pair, CordonStructureWorks works, Gun gun) {
        int front = CordonFront.frontward(cordon.plan());
        List<int[]> added = new ArrayList<>();
        double target = gun.limit() - 2;
        for (int i = 0; i + 1 < pair.chain().size(); i++) {
            int[] p = pair.chain().get(i), q = pair.chain().get(i + 1);
            double d = Math.hypot(q[0] - p[0], q[1] - p[1]);
            if (d <= gun.limit()) continue;
            int k = (int) Math.ceil(d / target) - 1;
            for (int j = 1; j <= k; j++) {
                double t = j / (double) (k + 1);
                int x = (int) Math.round(p[0] + (q[0] - p[0]) * t), y = (int) Math.round(p[1] + (q[1] - p[1]) * t);
                int[] at = roomNear(works, x, y, 10);
                if (at == null) {
                    System.out.printf("    [cordon-gap-look/nests] no room for a nest near %d,%d%n", x, y);
                    continue;
                }
                nest(works, at[0], at[1], front);
                added.add(at);
            }
        }
        System.out.printf("    [cordon-gap-look/nests] %d nests added: %s%n", added.size(), cellsText(added));
        return added;
    }

    /** The cell nearest {@code (x, y)} within {@code reach} with open floor round it: room for a nest. */
    private static int[] roomNear(CordonStructureWorks w, int x, int y, int reach) {
        for (int r = 0; r <= reach; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (roomFor(w, x + dx, y + dy)) return new int[]{x + dx, y + dy};
                }
            }
        }
        return null;
    }

    private static boolean roomFor(CordonStructureWorks w, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!w.grid.inBounds(x + dx, y + dy) || !PreparedGround.open(w.ctx, x + dx, y + dy)) return false;
            }
        }
        return true;
    }

    /** A sandbagged nest as the picket course digs one: bags across its front and both flanks. */
    private static void nest(CordonStructureWorks w, int x, int y, int front) {
        for (int k = -1; k <= 1; k++) w.sandbag(x + k, y + front, new int[]{0, front}, null);
        w.sandbag(x - 1, y, new int[]{-1, 0}, null);
        w.sandbag(x + 1, y, new int[]{1, 0}, null);
    }

    /**
     * Closer redoubts: whichever of the pair needs the shorter move goes along the line toward the other until the
     * chain closes, its walled compound copied cell for cell onto the new ground and its old ground left open. The
     * battle mans it there as it mans any redoubt.
     */
    static CordonSetup.CordonMap closer(CordonSetup.CordonMap cordon, Pair pair, CordonStructureWorks works, Gun gun) {
        MapResult map = cordon.map();
        List<Precinct> redoubts = CordonPlan.redoubts(cordon.plan());
        int front = CordonFront.frontward(cordon.plan());
        List<int[]> west = towersOf(map, redoubts, pair.west(), front);
        List<int[]> east = towersOf(map, redoubts, pair.east(), front);
        List<int[]> nests = courseNests(map, pair.west());
        // Which redoubt goes: {@code -Dsnapshot.cordonGap.move=east} (the default) or {@code west}, or {@code auto} for the
        // shorter move.
        String move = System.getProperty("snapshot.cordonGap.move", "east").trim();
        int bestShift = 0, bestWhich = -1;
        for (int s = 1; s <= 160 && bestWhich < 0; s++) {
            if (!move.equals("west")
                    && CordonGapLaw.widestStep(west, shifted(east, -s), nests) <= gun.limit() - 1) bestWhich = pair.east();
            else if (!move.equals("east")
                    && CordonGapLaw.widestStep(shifted(west, s), east, nests) <= gun.limit() - 1) bestWhich = pair.west();
            bestShift = s;
        }
        if (bestWhich < 0) throw new IllegalStateException("no move of either redoubt closes pair " + pair.west());
        int dx = bestWhich == pair.east() ? -bestShift : bestShift;
        Precinct moving = redoubts.get(bestWhich - 1);
        int[] box = CordonGarrisons.perimeterBox(map.topology, moving);
        if (box == null) throw new IllegalStateException("redoubt " + bestWhich + " has no wall");
        int m = 2;
        int x0 = box[0] - m, y0 = box[1] - m, x1 = box[2] + m, y1 = box[3] + m;
        int bw = x1 - x0 + 1, bh = y1 - y0 + 1;
        CordonStructureWorks.Ground[] ground = new CordonStructureWorks.Ground[bw * bh];
        String[] wall = new String[bw * bh];
        CellTopology topology = map.topology;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int i = (y - y0) * bw + (x - x0);
                ground[i] = CordonStructureWorks.Ground.of(topology, x, y);
                // Every wall goes with it, the houses inside as well as the perimeter; they go without roofs.
                wall[i] = topology.isWall(x, y) ? topology.getSurfaceId(x, y) : null;
            }
        }
        CordonStructureWorks.Ground yard = works.sample((x0 + x1) / 2, (y0 + y1) / 2,
                (x, y) -> x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3]);
        CordonStructureWorks.Ground outside = works.sample((x0 + x1) / 2, (y0 + y1) / 2,
                (x, y) -> (x < x0 || x > x1 || y < y0 || y > y1) && (x < x0 + dx || x > x1 + dx));
        works.clear(x0, y0, x1, y1, outside, null);
        works.clear(x0 + dx, y0, x1 + dx, y1, yard, null);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int i = (y - y0) * bw + (x - x0);
                if (wall[i] == null && ground[i] != null && ground[i].kind() != null) {
                    works.floor(x + dx, y, x + dx, y, ground[i]);
                }
            }
        }
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                String material = wall[(y - y0) * bw + (x - x0)];
                if (material != null) works.wall(x + dx, y, material);
            }
        }
        System.out.printf("    [cordon-gap-look/closer] redoubt %d moved %+d cells along the line (box %d,%d..%d,%d)%n",
                bestWhich, dx, box[0], box[1], box[2], box[3]);
        List<Precinct> precincts = new ArrayList<>();
        for (Precinct p : cordon.plan().precincts()) precincts.add(p == moving ? p.movedBy(dx, 0) : p);
        PrecinctPlan plan = cordon.plan();
        PrecinctPlan moved = new PrecinctPlan(precincts, plan.attackerFrom(), plan.standoff(), plan.lanes(),
                plan.unplacedLanePlaces(), plan.movedLaneWaypoints(), plan.landscape(), plan.layout());
        return new CordonSetup.CordonMap(map, moved, cordon.battleSeed(), cordon.corridor(), cordon.biome(),
                cordon.density());
    }

    private static List<int[]> shifted(List<int[]> cells, int dx) {
        List<int[]> out = new ArrayList<>();
        for (int[] c : cells) out.add(new int[]{c[0] + dx, c[1]});
        return out;
    }

    /** The longer gun's reach, as the law would read it. */
    static Gun longerGun() {
        WeaponDef w = TurretCatalogRegistry.requireStructure(LONGER).mount.weapon;
        return new Gun(w.range, w.hitSpread, w.aoeRadius);
    }

    /**
     * The installed turret catalog with the line's gun ({@code structure.turret-heavy-mg}) mounting the heavy mortar:
     * every gun the garrison seats on the line is then that emplacement, art and weapon, for this battle only.
     */
    private static TurretCatalogRegistry swapped(Path modRoot) throws Exception {
        TurretCatalogRegistry registry = new TurretCatalogRegistry();
        for (String path : TurretCatalogRegistry.BUILTIN_CATALOGS) {
            JSONObject root = new JSONObject(Files.readString(modRoot.resolve(path)));
            JSONArray structures = root.getJSONArray("structures");
            for (int i = 0; i < structures.length(); i++) {
                JSONObject s = structures.getJSONObject(i);
                if (TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID.equals(s.getString("id"))) s.put("mount", MORTAR_MOUNT);
            }
            registry.ingest(root, WeaponRegistry.installed());
        }
        return registry;
    }

    // ---------------------------------------------------------------- measuring the line

    static CordonSetup.CordonMap map(CordonVariant variant, CordonPlan.Density density, long seed) {
        return CordonSetup.map(variant, density, SurfacePalette.VERDANT, seed);
    }

    private static void survey(CordonVariant variant, String survey) {
        for (String part : survey.split(";")) {
            String[] kv = part.split(":");
            CordonPlan.Density density = CordonPlan.Density.valueOf(kv[0].trim().toUpperCase(Locale.ROOT));
            for (String s : kv[1].split(",")) {
                long seed = Long.parseLong(s.trim());
                CordonSetup.CordonMap cordon = map(variant, density, seed);
                StringBuilder line = new StringBuilder();
                for (CordonGapLaw.Gap gap : CordonSetup.gaps(cordon.map(), cordon.plan())) {
                    line.append(String.format(Locale.ROOT, " %d-%d %.0f%s (towers %.0f)", gap.west(), gap.east(),
                            gap.widestStep(), gap.holds() ? "" : "!", gap.towerToTower()));
                }
                System.out.printf("    [cordon-gap-look/survey] %s %s seed %d:%s%n", variant, density, seed, line);
            }
        }
    }

    /** The pair to show: the one asked for, else the widest broken one; with its chain. */
    static Pair pair(CordonSetup.CordonMap cordon, int wanted) {
        List<CordonGapLaw.Gap> gaps = CordonSetup.gaps(cordon.map(), cordon.plan());
        CordonGapLaw.Gap chosen = null;
        for (CordonGapLaw.Gap gap : gaps) {
            System.out.printf(Locale.ROOT, "    [cordon-gap-look] gap %d-%d widest %.1f%s, towers %.1f%n", gap.west(),
                    gap.east(), gap.widestStep(), gap.holds() ? "" : " BROKEN", gap.towerToTower());
            if (wanted > 0 ? gap.west() == wanted : (chosen == null || gap.widestStep() > chosen.widestStep())) chosen = gap;
        }
        if (chosen == null) throw new IllegalStateException("no pair " + wanted);
        MapResult map = cordon.map();
        List<Precinct> redoubts = CordonPlan.redoubts(cordon.plan());
        int front = CordonFront.frontward(cordon.plan());
        List<int[]> chain = chain(towersOf(map, redoubts, chosen.west(), front),
                towersOf(map, redoubts, chosen.east(), front), courseNests(map, chosen.west()));
        int widestFrom = 0;
        double widest = -1;
        for (int i = 0; i + 1 < chain.size(); i++) {
            double d = Math.hypot(chain.get(i + 1)[0] - chain.get(i)[0], chain.get(i + 1)[1] - chain.get(i)[1]);
            if (d > widest) {
                widest = d;
                widestFrom = i;
            }
        }
        System.out.printf(Locale.ROOT, "    [cordon-gap-look] chain %d-%d: %s%n", chosen.west(), chosen.east(),
                cellsText(chain));
        return new Pair(chosen.west(), chosen.east(), chain, widestFrom, widest);
    }

    /** The best chain of guns (the one whose widest step is least) from one of {@code from} to one of {@code to}. */
    static List<int[]> chain(List<int[]> from, List<int[]> to, List<int[]> between) {
        List<int[]> guns = new ArrayList<>(from);
        guns.addAll(between);
        int targets = guns.size();
        guns.addAll(to);
        int n = guns.size();
        double[] best = new double[n];
        int[] previous = new int[n];
        boolean[] done = new boolean[n];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        Arrays.fill(previous, -1);
        for (int i = 0; i < from.size(); i++) best[i] = 0;
        for (int round = 0; round < n; round++) {
            int next = -1;
            for (int i = 0; i < n; i++) if (!done[i] && (next < 0 || best[i] < best[next])) next = i;
            if (next < 0 || best[next] == Double.POSITIVE_INFINITY) break;
            if (next >= targets) {
                List<int[]> path = new ArrayList<>();
                for (int at = next; at >= 0; at = previous[at]) path.add(0, guns.get(at));
                return path;
            }
            done[next] = true;
            for (int i = 0; i < n; i++) {
                if (done[i]) continue;
                double step = Math.max(best[next], Math.hypot(guns.get(next)[0] - guns.get(i)[0],
                        guns.get(next)[1] - guns.get(i)[1]));
                if (step < best[i]) {
                    best[i] = step;
                    previous[i] = next;
                }
            }
        }
        throw new IllegalStateException("no chain of guns between the pair");
    }

    private static List<int[]> towersOf(MapResult map, List<Precinct> redoubts, int number, int front) {
        return CordonGarrisons.towers(map, redoubts.get(number - 1), number, front);
    }

    private static List<int[]> courseNests(MapResult map, int west) {
        Laid picket = map.layout.find(CordonPlan.PICKET_LINE + west + "-" + (west + 1));
        return picket == null || !picket.isLaid() ? List.of() : picket.nests();
    }

    /** Every gun the line seats: each redoubt's towers and every picket course's nests. */
    static List<int[]> lineGuns(CordonSetup.CordonMap cordon) {
        MapResult map = cordon.map();
        int front = CordonFront.frontward(cordon.plan());
        List<int[]> out = new ArrayList<>();
        List<Precinct> redoubts = CordonPlan.redoubts(cordon.plan());
        for (int n = 1; n <= redoubts.size(); n++) out.addAll(towersOf(map, redoubts, n, front));
        for (int n = 1; n < redoubts.size(); n++) out.addAll(courseNests(map, n));
        return out;
    }

    /** The middle of the pair's ground and the cells across that show both redoubts whole. */
    static float[] pairSpan(CordonSetup.CordonMap cordon, Pair pair) {
        List<Precinct> redoubts = CordonPlan.redoubts(cordon.plan());
        int[] w = CordonGarrisons.perimeterBox(cordon.map().topology, redoubts.get(pair.west() - 1));
        int[] e = CordonGarrisons.perimeterBox(cordon.map().topology, redoubts.get(pair.east() - 1));
        int lx = Math.min(w[0], e[0]), hx = Math.max(w[2], e[2]);
        int ly = Math.min(w[1], e[1]), hy = Math.max(w[3], e[3]);
        float across = Math.max(hx - lx + 40, (hy - ly + 40) * W / (float) H);
        return new float[]{(lx + hx) / 2f + 0.5f, (ly + hy) / 2f + 0.5f, across};
    }

    private static String cellsText(List<int[]> cells) {
        StringBuilder b = new StringBuilder();
        for (int[] c : cells) b.append(b.length() == 0 ? "" : " ").append(c[0]).append(',').append(c[1]);
        return b.toString();
    }
}
