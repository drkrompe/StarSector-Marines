package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationBrush;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationTestMaps;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The owner's elevation decisions as picture cards: cliff look, sight rule,
 * firing up and the swarm on cliffs, each option on the overlook test map at
 * play zoom (64x36 cells at 30 px, 1920x1080). Terrain shading is the CPU model
 * of the composite shader. Rings, routes and captions are review aids drawn
 * over the frame, not anything the game draws.
 */
public final class ElevationOptionsSnapshotSuite implements SnapshotSuite {

    private static final int CELL_PX = 30;
    private static final long SEED = 11L;
    private static final EnumSet<RenderLayer> GROUND_ONLY = EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS);
    private static final EnumSet<RenderLayer> WITH_UNITS = EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS,
            RenderLayer.UNIT_SHADOWS, RenderLayer.UNITS);

    /** Marines at the foot of the south cliff, west of the vehicle ramp. */
    private static final int[][] LOW_SQUAD = {{24, 6}, {26, 5}, {28, 6}, {25, 4}, {27, 4}, {29, 4}};
    /** Columns of the defenders on the south rim; their rows are found on each map. */
    private static final int[] RIM_COLUMNS = {24, 27, 30};
    /** Defenders further back on the top. */
    private static final int[][] BACK = {{25, 19}, {28, 21}, {31, 18}};

    @Override
    public String id() {
        return "elevation-options";
    }

    @Override
    public String label() {
        return "Elevation decisions for the owner";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        List<SnapshotArtifact> out = new ArrayList<>();

        // Cliff look.
        Object[][] looks = {
                {"a-narrow", plan(1, 0), "A  Narrow rock band: one-cell faces all round", false},
                {"b-wide", plan(2, 0), "B  Wide faces: two-cell faces all round", false},
                {"c-tall-south", plan(1, 2), "C  Tall south faces: one cell north, up to three facing the camera", false},
                {"d-lit-tops", plan(1, 2), "D  Tall south faces, and higher ground a step brighter in the sun", true},
        };
        for (Object[] look : looks) {
            MapResult map = stamped((ElevationPlan) look[1], ElevationMap.SightRule.SKYLINE);
            int[][] rim = rim(map);
            BufferedImage image = sunlitWithUnits(renderer, map, sim -> {
                spawn(sim, Faction.MARINE, UnitType.MARINE_BLUE, LOW_SQUAD);
                spawn(sim, Faction.MARINE, UnitType.MARINE_BLUE, rim);
                spawn(sim, Faction.MARINE, UnitType.MARINE_BLUE, BACK);
            }, null, (Boolean) look[3], rim);
            caption(image, (String) look[2], "Same plateau, ramps and marines; the sun is the battle's default.");
            out.add(new SnapshotArtifact("cliff-look/" + look[0] + ".png", image));
        }

        // Sight rule: what six marines at the foot of the cliff can see.
        Object[][] rules = {
                {"a-skyline", ElevationMap.SightRule.SKYLINE,
                        "A  Skyline: the low ground sees the face and the rim, nothing behind it"},
                {"b-strict", ElevationMap.SightRule.STRICT,
                        "B  Strict (StarCraft): the low ground sees nothing on top without a spotter"},
                {"c-flat", ElevationMap.SightRule.FLAT,
                        "C  Flat: height does not change sight; faces do not block it"},
        };
        for (Object[] rule : rules) {
            MapResult map = stamped(plan(1, 0), (ElevationMap.SightRule) rule[1]);
            int[][] rim = rim(map);
            AtomicReference<BattleSimulation> simRef = new AtomicReference<>();
            BufferedImage image = sunlitWithUnits(renderer, map, sim -> {
                simRef.set(sim);
                spawn(sim, Faction.MARINE, UnitType.MARINE_BLUE, LOW_SQUAD);
                spawn(sim, Faction.DEFENDER, UnitType.MARINE_RED, rim);
                spawn(sim, Faction.DEFENDER, UnitType.MARINE_RED, BACK);
            }, simRef, false, rim);
            caption(image, (String) rule[2], "Dark: ground the six blue marines cannot see. Red ring: a defender they cannot see.");
            out.add(new SnapshotArtifact("sight/" + rule[0] + ".png", image));
        }

        // Firing up: the same exchange under three accuracy rules.
        Object[][] fire = {
                {"a-none", "A  No uphill penalty: the rim holder has the lip's cover (15% of rounds stopped) and nothing more"},
                {"b-quarter", "B  Lip cover and uphill fire at 75% accuracy"},
                {"c-half", "C  Lip cover and uphill fire at 50% accuracy (StarCraft's miss chance)"},
        };
        for (Object[] option : fire) {
            MapResult map = stamped(plan(1, 0), ElevationMap.SightRule.SKYLINE);
            int[][] rim = rim(map);
            BufferedImage image = sunlitWithUnits(renderer, map, sim -> {
                spawn(sim, Faction.MARINE, UnitType.MARINE_BLUE, LOW_SQUAD);
                spawn(sim, Faction.DEFENDER, UnitType.MARINE_RED, rim);
            }, null, false, rim);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{8f, 6f}, 0f));
            for (int i = 0; i < 3; i++) {
                line(g, LOW_SQUAD[i], rim[i], new Color(255, 230, 120, 210));
                line(g, rim[i], LOW_SQUAD[i + 3], new Color(255, 120, 110, 160));
            }
            g.dispose();
            caption(image, (String) option[1], "Yellow: fire up at the rim. Red: fire down, at full accuracy in every option.");
            out.add(new SnapshotArtifact("firing-up/" + option[0] + ".png", image));
        }

        // The swarm and cliffs: where a pack from the west goes to reach the top.
        MapResult map = stamped(plan(1, 0), ElevationMap.SightRule.SKYLINE);
        int[][] pack = {{3, 24}, {4, 26}, {6, 25}, {3, 22}, {5, 23}};
        int[] goal = {30, 21};
        Object[][] swarm = {
                {"a-no-climb", "A  No climbing: every strain goes round to a ramp; ramps are the chokepoints", false, false},
                {"b-climb-slowly", "B  Runners climb slowly: straight up any face, seconds on it and unable to fight", true, true},
                {"c-climbing-strain", "C  Only a climbing strain (a later noun) scales faces; Runners use the ramps", true, false},
        };
        for (Object[] option : swarm) {
            BufferedImage image = sunlitWithUnits(renderer, map, sim -> {
                spawn(sim, Faction.DEFENDER, UnitType.SWARM_RUNNER, pack);
                // A marine watching them, so the fog shows the pack.
                spawn(sim, Faction.MARINE, UnitType.MARINE_BLUE, new int[][]{{10, 21}});
            }, null, false, new int[0][]);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            boolean climbers = (Boolean) option[2];
            boolean runnersClimb = (Boolean) option[3];
            if (!runnersClimb) path(g, map.grid, pack[0], goal, new Color(255, 200, 90, 230));
            if (climbers) {
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                        new float[]{10f, 7f}, 0f));
                line(g, pack[1], goal, new Color(255, 120, 200, 230));
            }
            g.dispose();
            caption(image, (String) option[1], climbers
                    ? (runnersClimb ? "Pink: the pack's straight climb." : "Orange: Runners round to the ramp. Pink: a climbing strain's straight line.")
                    : "Orange: the pack's route to the top, round to the west ramp.");
            out.add(new SnapshotArtifact("swarm-climb/" + option[0] + ".png", image));
        }
        return out;
    }

    private static ElevationPlan plan(int faceWidth, int southFace) {
        ElevationPlan base = ElevationTestMaps.overlook(faceWidth);
        return new ElevationPlan(base.width(), base.height(), faceWidth, southFace, base.ruggedness(), base.seed(),
                base.plateaus(), base.ramps());
    }

    private static MapResult stamped(ElevationPlan plan, ElevationMap.SightRule rule) {
        String before = System.getProperty("battle.elevation.sight");
        System.setProperty("battle.elevation.sight", rule.name().toLowerCase());
        try {
            MapResult map = ElevationTestMaps.meadow(CellTopology.GroundKind.GRASS);
            ElevationBrush.stamp(plan, map.grid, map.topology);
            return map;
        } finally {
            if (before == null) System.clearProperty("battle.elevation.sight");
            else System.setProperty("battle.elevation.sight", before);
        }
    }

    /**
     * Units over a sunlit ground: the ground is shaded on its own, and the
     * pixels the unit layers change are laid back over it, so the sun never
     * darkens a body. With {@code fog}, ground the marines cannot see is dimmed
     * and every defender they cannot see is ringed.
     */
    /** The first walkable top cell north of the cliff foot in each rim column. */
    private static int[][] rim(MapResult map) {
        ElevationMap e = map.grid.elevation();
        int[][] out = new int[RIM_COLUMNS.length][];
        for (int i = 0; i < RIM_COLUMNS.length; i++) {
            int x = RIM_COLUMNS[i];
            for (int y = 0; y < ElevationTestMaps.HEIGHT; y++) {
                if (e.tier(x, y) == 1 && !e.isFace(x, y) && !e.isRamp(x, y)) {
                    out[i] = new int[]{x, y};
                    break;
                }
            }
        }
        return out;
    }

    private static BufferedImage sunlitWithUnits(HeadlessBattleMapRenderer renderer, MapResult map,
                                                 Consumer<BattleSimulation> populate,
                                                 AtomicReference<BattleSimulation> fog,
                                                 boolean litTops, int[][] rim) {
        float cx = ElevationTestMaps.WIDTH / 2f, cy = ElevationTestMaps.HEIGHT / 2f;
        int w = ElevationTestMaps.WIDTH, h = ElevationTestMaps.HEIGHT;
        BufferedImage ground = renderer.renderView(map, SEED, cx, cy, w, h, CELL_PX, populate, GROUND_ONLY);
        BufferedImage units = renderer.renderView(map, SEED, cx, cy, w, h, CELL_PX, populate, WITH_UNITS);
        if (litTops) brightenTops(map, ground);
        BufferedImage lit = ElevationSnapshotSuite.sunlit(map, ground);
        BufferedImage out = new BufferedImage(lit.getWidth(), lit.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                int g = ground.getRGB(x, y), u = units.getRGB(x, y);
                out.setRGB(x, y, g == u ? lit.getRGB(x, y) : u);
            }
        }
        if (fog != null && fog.get() != null) {
            BattleSimulation sim = fog.get();
            boolean[] revealed = sim.getFogOfWar().cellRevealedArray();
            Graphics2D g = out.createGraphics();
            g.setColor(new Color(0, 0, 0, 150));
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (!revealed[y * w + x]) g.fillRect(x * CELL_PX, (h - 1 - y) * CELL_PX, CELL_PX, CELL_PX);
                }
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(3f));
            g.setColor(new Color(255, 80, 70, 230));
            NavigationGrid grid = map.grid;
            for (int[][] group : new int[][][]{rim, BACK}) {
                for (int[] d : group) {
                    if (d == null) continue;
                    boolean seen = false;
                    for (int[] m : LOW_SQUAD) seen |= grid.hasLineOfSight(m[0], m[1], d[0], d[1]);
                    if (!seen) {
                        int px = (int) ((d[0] + .5f) * CELL_PX), py = (int) ((h - d[1] - .5f) * CELL_PX);
                        g.drawOval(px - 20, py - 20, 40, 40);
                    }
                }
            }
            g.dispose();
        }
        return out;
    }

    /**
     * A CPU stand-in for a lighting change the composite does not make today:
     * the ground on each tier lifted by a step, so higher ground catches more
     * of the sun. Shown so the owner can judge it before it is built.
     */
    private static void brightenTops(MapResult map, BufferedImage ground) {
        ElevationMap e = map.grid.elevation();
        int h = ElevationTestMaps.HEIGHT;
        for (int py = 0; py < ground.getHeight(); py++) {
            for (int px = 0; px < ground.getWidth(); px++) {
                int x = px / CELL_PX, y = h - 1 - py / CELL_PX;
                if (e.isFace(x, y)) continue;
                int tier = e.tier(x, y);
                if (tier == 0) continue;
                float lift = 1f + .2f * tier;
                int rgb = ground.getRGB(px, py);
                int r = Math.min(255, (int) (((rgb >> 16) & 255) * lift));
                int gr = Math.min(255, (int) (((rgb >> 8) & 255) * lift));
                int b = Math.min(255, (int) ((rgb & 255) * lift));
                ground.setRGB(px, py, (rgb & 0xFF000000) | (r << 16) | (gr << 8) | b);
            }
        }
    }

    private static void spawn(BattleSimulation sim, Faction faction, UnitType type, int[][] cells) {
        int n = 0;
        for (int[] c : cells) {
            if (c == null || !sim.getGrid().isWalkable(c[0], c[1])) continue;
            sim.spawn(new EntitySpec(faction.name().toLowerCase() + "-" + type.name().toLowerCase() + "-" + n++,
                    faction, type, c[0], c[1]));
        }
    }

    private static void line(Graphics2D g, int[] a, int[] b, Color color) {
        int h = ElevationTestMaps.HEIGHT;
        g.setColor(color);
        g.drawLine((int) ((a[0] + .5f) * CELL_PX), (int) ((h - a[1] - .5f) * CELL_PX),
                (int) ((b[0] + .5f) * CELL_PX), (int) ((h - b[1] - .5f) * CELL_PX));
    }

    private static void path(Graphics2D g, NavigationGrid grid, int[] from, int[] to, Color color) {
        int[] route = GridPathfinder.findPath(grid, from[0], from[1], to[0], to[1]);
        if (route == null) return;
        int h = ElevationTestMaps.HEIGHT;
        g.setColor(color);
        for (int i = 0; i + 3 < route.length; i += 2) {
            g.drawLine((int) ((route[i] + .5f) * CELL_PX), (int) ((h - route[i + 1] - .5f) * CELL_PX),
                    (int) ((route[i + 2] + .5f) * CELL_PX), (int) ((h - route[i + 3] - .5f) * CELL_PX));
        }
    }

    static void caption(BufferedImage image, String title, String note) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(10, 14, 20, 200));
        g.fillRect(0, 0, image.getWidth(), 74);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        g.setColor(new Color(0xE8EEF6));
        g.drawString(title, 20, 32);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        g.setColor(new Color(0xB8C4D4));
        g.drawString(note, 20, 62);
        g.dispose();
    }
}
