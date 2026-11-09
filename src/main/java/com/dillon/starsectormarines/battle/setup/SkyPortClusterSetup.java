package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.GroundMarineArrival;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.VoidBackdrop;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * A disposable diagnostic battle on a sky port tower cluster (`voids.md`): three tower roofs over the city drop,
 * every cell between them void, joined by walkways of different widths, with lower towers standing in the drop all
 * round. Tower A, where the company lands, reaches tower B by a two-cell catwalk and tower C by a catwalk that turns
 * a corner over the drop; B and C are joined by a six-cell skybridge a vehicle could cross. Each roof carries a
 * landing pad or blocks of plant. Tower C stands a tier ({@link ElevationMap#TIER_RISE_METERS}) above the others, and
 * the dogleg and the skybridge climb onto it by a ramp, their last {@link #STAIR_CELLS} cells, so the only ways up are
 * the stairs and C's holders see down across the drop. No enemy and no end: the Diagnostics tab of the debug missions lists it, and
 * {@code gradlew playBattle -Pbattle=sky-port} opens it straight away.
 *
 * <p>The look suite and the {@code walkway-crossing} behaviour scene lay the same ground through {@link #lay}.
 */
public final class SkyPortClusterSetup {

    public static final long SEED = 11L;
    public static final int W = 240, H = 170;
    /** The towers' roofs, inclusive: {@code {x0, y0, x1, y1}}. */
    public static final int[] A = {30, 60, 80, 105}, B = {120, 95, 165, 145}, C = {115, 10, 160, 50};
    /** The walkways, inclusive: A to B, B to C, and the dogleg from A to C in its two legs. */
    public static final int[] CATWALK = {81, 99, 119, 100}, SKYBRIDGE = {136, 51, 141, 94};
    public static final int[][] DOGLEG = {{70, 30, 71, 59}, {72, 30, 114, 31}};
    private static final int[][] WALKWAYS = {CATWALK, SKYBRIDGE, DOGLEG[0], DOGLEG[1]};
    /**
     * A lone roof in the far corner, reached by no walkway and out of sight of the cluster: where a scene keeps
     * the other side, which a battle needs on the map to run.
     */
    public static final int[] OUTPOST = {231, 161, 238, 168};
    /** The landing pads, inclusive. */
    private static final int[][] PADS = {{54, 70, 74, 90}, {138, 26, 154, 44}};
    /** The tier tower C stands on, and how many cells of each walkway into it climb to it. */
    public static final int C_TIER = 1, STAIR_CELLS = 6;
    /** Where the company lands: tower A, beside its pad. */
    private static final int ARRIVAL_X = 62, ARRIVAL_Y = 96;
    /** How far below the roofs the city lies, in metres. */
    public static final float DEPTH = 400f;
    /**
     * How high above the roofs the camera looks down from, in metres, at every zoom: high enough that close in the
     * canyons between towers keep their proportions instead of plunging, low enough that the drop still moves
     * against the roofs when the view pans.
     */
    public static final float EYE_METERS = 240f;

    /** Vanilla hulls flying the bays and skyways; the last three, the freighters, stand parked on the terraces. */
    public static final List<String> CRAFT = List.of(
            "graphics/ships/aeroshuttle/aeroshuttle_base.png", "graphics/ships/hermes/hermes_base.png",
            "graphics/ships/hound/hound_base.png", "graphics/ships/wayfarer/wayfarer.png",
            "graphics/ships/buffalo/buffalo_base.png", "graphics/ships/tarsus/tarsus_base.png",
            "graphics/ships/mule/mule_base.png");

    /** A map's grid and topology, laid and not yet a battle. */
    public record Ground(NavigationGrid grid, CellTopology topology) { }

    private SkyPortClusterSetup() { }

    public static BattleSimulation create(long seed) {
        Ground ground = lay(true);
        BattleSimulation sim = new BattleSimulation(ground.grid(), ground.topology(), seed);
        sim.setSurface(SurfacePalette.ARID);
        sim.setGroundMarineArrival(new GroundMarineArrival(ARRIVAL_X, ARRIVAL_Y,
                SquadCommandClaim.mission("sky-port", "Company walks the towers")));
        sim.playerCommandsCompany();
        sim.addObjective(new WalkTheTowers());
        return sim;
    }

    /** The city's street grid round the port: a plot's pitch and the street between plots, in metres. */
    public static final int PLOT_PITCH = 60, STREET = 24;
    /** The least side of a plot left standing once trimmed clear of the port's towers. */
    private static final int MIN_PLOT = 14;
    /** How far round the map the city's towers stand, in metres. */
    private static final int CITY_MARGIN = 110;

    /**
     * The industrial hive sky port seen from its roofs: slender towers stepping out only a little as they fall, so
     * the streets between them stay open, and the city's towers round them on a street grid. {@code depth} scales
     * the terraces and the city's roofs with it; {@code eye} is the camera's height above the roofs in metres.
     */
    public static VoidBackdrop backdrop(float depth, float eye) {
        float f = depth / 400f;
        // Two bold setbacks rather than many slight ones: a terrace a couple of metres deep reads as a sliver.
        return industrial(depth, eye, List.of(new VoidBackdrop.Tier(110f * f, 4f),
                new VoidBackdrop.Tier(250f * f, 9f)), city(f), skyways(f));
    }

    /**
     * Traffic down the city's streets: every street of the grid that keeps clear of the port's towers carries a
     * stack of skyways, one above another in its canyon, kept above the deep terraces that narrow it. The streets
     * one way fly at other depths than the streets across them, so no two lanes cross at one depth.
     */
    static List<VoidBackdrop.Skyway> skyways(float f) {
        List<VoidBackdrop.Skyway> ways = new ArrayList<>();
        int[][] port = {A, B, C, OUTPOST};
        float lo = -CITY_MARGIN, hiX = W + CITY_MARGIN, hiY = H + CITY_MARGIN;
        float[][] depths = {{50f, 115f, 180f}, {80f, 148f, 215f}};
        for (int axis = 0; axis < 2; axis++) {
            float hi = axis == 0 ? hiX : hiY;
            for (int street = -CITY_MARGIN + PLOT_PITCH - STREET / 2; street < hi; street += PLOT_PITCH) {
                boolean clear = true;
                for (int[] roof : port) {
                    int a0 = axis == 0 ? roof[0] : roof[1], a1 = axis == 0 ? roof[2] : roof[3];
                    if (street >= a0 - STREET / 2 && street <= a1 + STREET / 2) clear = false;
                }
                if (!clear) continue;
                for (float depth : depths[axis]) {
                    float z = depth * f;
                    ways.add(axis == 0 ? new VoidBackdrop.Skyway(street, lo, street, hiY, z)
                            : new VoidBackdrop.Skyway(lo, street, hiX, street, z));
                }
            }
        }
        return ways;
    }

    /**
     * The city's towers round the port, on a street grid: square plots of {@link #PLOT_PITCH} less a street of
     * {@link #STREET}, each trimmed to keep a street's width from every roof the battle stands on, and dropped if
     * too little is left. The city rises toward the port: a roof's depth grows with its plot's distance from the
     * cluster's middle, with a little of each plot's own.
     */
    static List<VoidBackdrop.Tower> city(float f) {
        List<VoidBackdrop.Tower> towers = new ArrayList<>();
        int[][] port = {A, B, C, OUTPOST};
        float mx = W / 2f, my = H / 2f;
        for (int py = -CITY_MARGIN; py < H + CITY_MARGIN; py += PLOT_PITCH) {
            for (int px = -CITY_MARGIN; px < W + CITY_MARGIN; px += PLOT_PITCH) {
                int[] plot = {px, py, px + PLOT_PITCH - STREET - 1, py + PLOT_PITCH - STREET - 1};
                for (int[] roof : port) {
                    plot = clear(plot, new int[]{roof[0] - STREET, roof[1] - STREET, roof[2] + STREET, roof[3] + STREET});
                    if (plot == null) break;
                }
                if (plot == null) continue;
                float cx = (plot[0] + plot[2]) / 2f, cy = (plot[1] + plot[3]) / 2f;
                float reach = (float) Math.hypot(cx - mx, cy - my);
                int own = Math.floorMod(px * 73 + py * 151, 7);
                float roofDepth = Math.min(260f, 28f + reach * .55f + own * 9f);
                towers.add(new VoidBackdrop.Tower(plot[0], plot[1], plot[2] + 1, plot[3] + 1, roofDepth * f));
            }
        }
        return towers;
    }

    /**
     * The plot trimmed off one side so it no longer meets {@code keepOut}, keeping the most of it; null if what is
     * left is narrower than {@link #MIN_PLOT}. Rectangles are inclusive.
     */
    static int[] clear(int[] plot, int[] keepOut) {
        if (plot[2] < keepOut[0] || plot[0] > keepOut[2] || plot[3] < keepOut[1] || plot[1] > keepOut[3]) return plot;
        int[][] options = {
                {plot[0], plot[1], keepOut[0] - 1, plot[3]}, {keepOut[2] + 1, plot[1], plot[2], plot[3]},
                {plot[0], plot[1], plot[2], keepOut[1] - 1}, {plot[0], keepOut[3] + 1, plot[2], plot[3]}};
        int[] best = null;
        long bestArea = 0;
        for (int[] o : options) {
            int w = o[2] - o[0] + 1, h = o[3] - o[1] + 1;
            if (w < MIN_PLOT || h < MIN_PLOT) continue;
            if ((long) w * h > bestArea) {
                bestArea = (long) w * h;
                best = o;
            }
        }
        return best;
    }

    /** The industrial hive city, facade and terrace roof, with the given terraces and neighbouring towers. */
    public static VoidBackdrop industrial(float depth, float eye, List<VoidBackdrop.Tier> tiers,
                                         List<VoidBackdrop.Tower> neighbours, List<VoidBackdrop.Skyway> skyways) {
        return new VoidBackdrop("graphics/battle/voids/industrial-city-floor.png", depth, Math.max(96f, depth),
                .42f, .41f, .40f,
                "graphics/battle/voids/industrial-facade.png", 32f,
                "graphics/battle/voids/industrial-terrace-roof.png", 40f, tiers, eye, CRAFT, .11f, neighbours, skyways);
    }

    /**
     * The cluster's ground with no one on it, under {@link #backdrop} at {@link #DEPTH}; {@code catwalk} false
     * leaves the drop where the catwalk from A to B would be, so A reaches B only round by C.
     */
    public static Ground lay(boolean catwalk) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                String finish = !catwalk && inside(x, y, CATWALK) ? null : finish(x, y);
                if (finish != null) {
                    grid.setWalkableFloor(x, y);
                    topology.setGroundKind(x, y, GroundKind.STRIPED);
                    topology.setSurface(x, y, topology.surfaceIndex(finish));
                } else {
                    grid.setWalkable(x, y, false);
                    grid.setTag(x, y, CellTag.SEE_THROUGH, true);
                    grid.setTag(x, y, CellTag.NO_EDGE_COVER, true);
                    grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
                    topology.setGroundKind(x, y, GroundKind.VOID);
                }
            }
        }
        block(grid, topology, 36, 66, 48, 78);
        block(grid, topology, 38, 88, 46, 98);
        block(grid, topology, 128, 118, 144, 134);
        block(grid, topology, 148, 100, 160, 110);
        block(grid, topology, 150, 126, 158, 138);
        block(grid, topology, 121, 15, 133, 27);
        raise(grid, catwalk);
        topology.setVoidBackdrop(backdrop(DEPTH, EYE_METERS));
        return new Ground(grid, topology);
    }

    /**
     * Tower C on {@link #C_TIER}, with stairs: the last {@link #STAIR_CELLS} cells of the dogleg's east leg and of the
     * skybridge are a ramp up to it. The drop round every roof is void, not ground, so no cliff face is needed: the
     * only walkable cells of different tiers that meet are a ramp's head and C, and any shared edge between walkable
     * cells the tiers would separate is closed, as the elevation brush closes a cliff edge.
     */
    private static void raise(NavigationGrid grid, boolean catwalk) {
        byte[] tier = new byte[W * H], kind = new byte[W * H];
        double[] rise = new double[W * H];
        for (int y = C[1]; y <= C[3]; y++) for (int x = C[0]; x <= C[2]; x++) tier[y * W + x] = C_TIER;
        int[] leg = DOGLEG[1];
        for (int x = leg[2] - STAIR_CELLS + 1; x <= leg[2]; x++) {
            for (int y = leg[1]; y <= leg[3]; y++) stair(tier, kind, rise, x, y, x - (leg[2] - STAIR_CELLS + 1));
        }
        for (int y = SKYBRIDGE[1]; y < SKYBRIDGE[1] + STAIR_CELLS; y++) {
            for (int x = SKYBRIDGE[0]; x <= SKYBRIDGE[2]; x++) stair(tier, kind, rise, x, y, SKYBRIDGE[1] + STAIR_CELLS - 1 - y);
        }
        ElevationMap map = ElevationMap.of(W, H, tier, kind, rise, ElevationMap.SightRule.configured());
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!grid.isWalkable(x, y)) continue;
                int i = y * W + x;
                if (x + 1 < W && grid.isWalkable(x + 1, y) && map.separates(i, i + 1)) grid.blockSharedEdge(x, y, Direction.E);
                if (y + 1 < H && grid.isWalkable(x, y + 1) && map.separates(i, i + W)) grid.blockSharedEdge(x, y, Direction.N);
            }
        }
        grid.setElevation(map);
    }

    /** A ramp cell climbing from C's lower tier, {@code step} cells up from its foot. */
    private static void stair(byte[] tier, byte[] kind, double[] rise, int x, int y, int step) {
        int i = y * W + x;
        tier[i] = C_TIER - 1;
        kind[i] = ElevationMap.RAMP;
        rise[i] = (step + .5) / STAIR_CELLS;
    }

    /** What a cell is laid with, or null where it is void. */
    private static String finish(int x, int y) {
        for (int[] w : WALKWAYS) if (inside(x, y, w)) return "ship-bay.grate-run";
        for (int[] t : new int[][]{A, B, C, OUTPOST}) {
            if (!inside(x, y, t)) continue;
            for (int[] pad : PADS) {
                if (!inside(x, y, pad)) continue;
                boolean rim = x == pad[0] || x == pad[2] || y == pad[1] || y == pad[3];
                return rim ? "apron.keep-clear" : "sky-port.pad-plate";
            }
            return "sky-port.deck-plate";
        }
        return null;
    }

    /** A walled block of roof plant: closed walls with one door. */
    private static void block(NavigationGrid grid, CellTopology topology, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                topology.setGroundKind(x, y, GroundKind.INDOOR);
                topology.setSurface(x, y, topology.surfaceIndex("loading-bay.staging-deck"));
                if (x != x0 && x != x1 && y != y0 && y != y1) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, 400);
                topology.setTag(x, y, CellTopology.Tag.WALL, true);
                topology.setSurface(x, y, topology.surfaceIndex("installation.dark-concrete-wall"));
            }
        }
        int doorX = (x0 + x1) / 2;
        grid.setWalkableFloor(doorX, y0);
        grid.setDoorway(doorX, y0, true);
        topology.setTag(doorX, y0, CellTopology.Tag.WALL, false);
        topology.setSurface(doorX, y0, topology.surfaceIndex("loading-bay.staging-deck"));
    }

    /** Whether a cell lies inside an inclusive rectangle {@code {x0, y0, x1, y1}}. */
    public static boolean inside(int x, int y, int[] r) {
        return x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3];
    }

    /** The diagnostic's standing marker: no win, no loss. */
    private static final class WalkTheTowers implements Objective {
        @Override public Faction owningFaction() { return Faction.MARINE; }
        @Override public void tick(BattleView sim) { }
        @Override public boolean isComplete() { return false; }
        @Override public boolean isFailed() { return false; }
        @Override public String displayName() { return Strings.get("skyPortObjective"); }
    }
}
