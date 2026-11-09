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
 * A disposable diagnostic battle on a sky port built as a terminus station (`voids.md`; owner, 2026-11-09: towers
 * spread so far apart that it was unclear why a city would build them so). Every cell off its decks is void over
 * the city drop. From the west edge in:
 *
 * <ul>
 *   <li>the <b>landing deck</b> on the map's west edge, with two pads, where the company lands;</li>
 *   <li>two <b>viaducts</b> east from it, a six-cell one a vehicle could cross and a three-cell one, each climbing
 *       its last {@link #STAIR_CELLS} cells to</li>
 *   <li>the <b>concourse</b>, the station's head hall, a tier ({@link ElevationMap#TIER_RISE_METERS}) above the
 *       rest, with its halls of plant;</li>
 *   <li>five <b>platforms</b> running east from it, each reached from the concourse by a stair bridge down across
 *       the gap between them, with the <b>track canyons</b> between the platforms, where craft fly in to berth at
 *       the platforms' heads as trains run into a terminus;</li>
 *   <li><b>footbridges</b> across the canyons, staggered, so a platform reaches its neighbour without going back
 *       up to the concourse.</li>
 * </ul>
 *
 * <p>The decks of one tier stand on one structure, so below the canyons the station's terraces join in one podium.
 * No enemy and no end: the Diagnostics tab of the debug missions lists it, and {@code gradlew playBattle
 * -Pbattle=sky-port} opens it straight away. The look suite and the {@code walkway-crossing} behaviour scene lay
 * the same ground through {@link #lay}.
 */
public final class SkyPortClusterSetup {

    public static final long SEED = 11L;
    public static final int W = 360, H = 240;
    /** The landing deck on the map's west edge, inclusive: {@code {x0, y0, x1, y1}}. */
    public static final int[] LANDING = {3, 66, 48, 173};
    /** The concourse, the station's raised head hall. */
    public static final int[] CONCOURSE = {74, 40, 123, 199};
    /** The viaducts from the landing deck up to the concourse: the wide one and the narrow one. */
    public static final int[] VIADUCT_N = {49, 138, 73, 143}, VIADUCT_S = {49, 96, 73, 98};
    /** The platforms: how many, where they run from and to, the first one's near side, their width and pitch. */
    public static final int PLATFORM_COUNT = 5, PLATFORM_X0 = 136, PLATFORM_X1 = 301, PLATFORM_Y0 = 53,
            PLATFORM_WIDTH = 14, PLATFORM_PITCH = 30;
    /** Each platform, inclusive, and the stair bridge from the concourse down to it. */
    public static final int[][] PLATFORMS = new int[PLATFORM_COUNT][], STAIRWAYS = new int[PLATFORM_COUNT][];
    static {
        for (int i = 0; i < PLATFORM_COUNT; i++) {
            int y0 = PLATFORM_Y0 + i * PLATFORM_PITCH;
            PLATFORMS[i] = new int[]{PLATFORM_X0, y0, PLATFORM_X1, y0 + PLATFORM_WIDTH - 1};
            STAIRWAYS[i] = new int[]{CONCOURSE[2] + 1, y0 + 5, PLATFORM_X0 - 1, y0 + 8};
        }
    }
    /** The footbridges across the track canyons, the first across the canyon between the first two platforms. */
    public static final int[][] FOOTBRIDGES = {{200, 67, 202, 82}, {255, 97, 257, 112}, {200, 127, 202, 142},
            {255, 157, 257, 172}};
    /**
     * A lone roof in the far corner, reached by no walkway and out of sight of the station: where a scene keeps
     * the other side, which a battle needs on the map to run.
     */
    public static final int[] OUTPOST = {350, 4, 357, 11};
    /** Everything the battle stands on but the walkways, the platforms as one block, for the city to keep clear of. */
    static final int[][] PORT = {LANDING, CONCOURSE,
            {PLATFORM_X0, PLATFORM_Y0, PLATFORM_X1, PLATFORM_Y0 + (PLATFORM_COUNT - 1) * PLATFORM_PITCH + PLATFORM_WIDTH - 1},
            OUTPOST};
    /** The landing pads on the landing deck, inclusive. */
    private static final int[][] PADS = {{9, 74, 39, 104}, {9, 135, 39, 165}};
    /** The tier the concourse stands on, and how many cells of each walkway onto it climb to it. */
    public static final int CONCOURSE_TIER = 1, STAIR_CELLS = 6;
    /** Where the company lands: the landing deck, between its pads. */
    public static final int ARRIVAL_X = 26, ARRIVAL_Y = 119;
    /** How far below the decks the city lies, in metres. */
    public static final float DEPTH = 400f;
    /**
     * How high above the roofs the camera looks down from, in metres, at every zoom: high enough that close in the
     * canyons between towers keep their proportions instead of plunging, low enough that the drop still moves
     * against the roofs when the view pans.
     */
    public static final float EYE_METERS = 240f;

    /** Vanilla hulls flying the bays, slips and skyways, mostly the shuttle and the Hound (owner); the last three, the freighters, stand parked on the terraces. */
    public static final List<String> CRAFT = List.of(
            "graphics/ships/aeroshuttle/aeroshuttle_base.png", "graphics/ships/hound/hound_base.png",
            "graphics/ships/hermes/hermes_base.png", "graphics/ships/wayfarer/wayfarer.png",
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
                SquadCommandClaim.mission("sky-port", "Company walks the station")));
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
     * The industrial hive sky port seen from its decks: the station's halls and platforms stepping out a little at
     * the first terrace, so the track canyons stay open above it, and joining in one podium at the second, and the
     * city's towers round it on a street grid. {@code depth} scales the terraces and the city's roofs with it;
     * {@code eye} is the camera's height above the roofs in metres.
     */
    public static VoidBackdrop backdrop(float depth, float eye) {
        float f = depth / 400f;
        // Two bold setbacks rather than many slight ones: a terrace a couple of metres deep reads as a sliver.
        return industrial(depth, eye, List.of(new VoidBackdrop.Tier(110f * f, 4f),
                new VoidBackdrop.Tier(250f * f, 14f)), city(f), skyways(f), slips(f));
    }

    /**
     * Traffic down the city's streets: every street of the grid that keeps clear of the port's towers carries a
     * skyway well down its canyon (owner, 2026-11-09: traffic too close), above the deep terraces that narrow it,
     * each street at its own depth. The streets one way fly at other depths than the streets across them, so no
     * two lanes cross at one depth; craft turning from one to the other climb or sink through the turn.
     */
    static List<VoidBackdrop.Skyway> skyways(float f) {
        List<VoidBackdrop.Skyway> ways = new ArrayList<>();
        int[][] port = PORT;
        float lo = -CITY_MARGIN, hiX = W + CITY_MARGIN, hiY = H + CITY_MARGIN;
        float[][] depths = {{95f, 130f, 110f, 150f, 120f}, {105f, 140f, 115f, 160f, 125f}};
        int lane = 0;
        for (int axis = 0; axis < 2; axis++) {
            float hi = axis == 0 ? hiX : hiY;
            for (int street = -CITY_MARGIN + PLOT_PITCH - STREET / 2; street < hi; street += PLOT_PITCH) {
                boolean clear = true;
                for (int[] roof : port) {
                    int a0 = axis == 0 ? roof[0] : roof[1], a1 = axis == 0 ? roof[2] : roof[3];
                    if (street >= a0 - STREET / 2 && street <= a1 + STREET / 2) clear = false;
                }
                if (!clear) continue;
                float z = depths[axis][lane++ % depths[axis].length] * f;
                ways.add(axis == 0 ? new VoidBackdrop.Skyway(street, lo, street, hiY, z)
                        : new VoidBackdrop.Skyway(lo, street, hiX, street, z));
            }
        }
        return ways;
    }

    /**
     * The city's towers round the port, on a street grid: square plots of {@link #PLOT_PITCH} less a street of
     * {@link #STREET}, each trimmed to keep a street's width from every roof the battle stands on, and dropped if
     * too little is left. The city rises toward the port: a roof's depth grows with its plot's distance from the
     * station's middle, with a little of each plot's own.
     */
    static List<VoidBackdrop.Tower> city(float f) {
        List<VoidBackdrop.Tower> towers = new ArrayList<>();
        int[][] port = PORT;
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

    /** The industrial hive city, facade and terrace roof, with the given terraces, neighbouring towers and lanes. */
    public static VoidBackdrop industrial(float depth, float eye, List<VoidBackdrop.Tier> tiers,
                                         List<VoidBackdrop.Tower> neighbours, List<VoidBackdrop.Skyway> skyways,
                                         List<VoidBackdrop.Slip> slips) {
        // Dusk: the drop fades into a deep slate blue rather than grey, so the lights painted in the art, lifted out
        // and laid over the haze, carry the city (owner, 2026-11-09: a sickly grey city devoid of light).
        float f = depth / 400f;
        VoidBackdrop.Atmosphere air = new VoidBackdrop.Atmosphere(
                "graphics/battle/voids/industrial-facade-glow.png", "graphics/battle/voids/industrial-terrace-roof-glow.png",
                "graphics/battle/voids/industrial-city-floor-glow.png", "graphics/fx/nebula_colorless.png", 4,
                List.of(165f * f, 300f * f), .72f, 70f);
        return new VoidBackdrop("graphics/battle/voids/industrial-city-floor.png", depth, Math.max(96f, depth),
                .20f, .24f, .34f,
                "graphics/battle/voids/industrial-facade.png", 32f,
                "graphics/battle/voids/industrial-terrace-roof.png", 40f, tiers, eye, CRAFT, .09f, neighbours, skyways,
                air, slips, List.of());
    }

    /**
     * The station's ground with no one on it, under {@link #backdrop} at {@link #DEPTH}; {@code footbridge} false
     * leaves the drop where the first footbridge would be, so the first two platforms reach each other only by
     * way of the concourse.
     */
    public static Ground lay(boolean footbridge) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                String finish = !footbridge && inside(x, y, FOOTBRIDGES[0]) ? null : finish(x, y);
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
        // The landing deck's shed, the concourse's halls, and kiosks on two platforms.
        block(grid, topology, 6, 112, 16, 126);
        block(grid, topology, 84, 100, 110, 140);
        block(grid, topology, 82, 52, 100, 70);
        block(grid, topology, 82, 170, 100, 188);
        block(grid, topology, 170, 57, 180, 62);
        block(grid, topology, 230, 147, 240, 152);
        raise(grid);
        topology.setVoidBackdrop(backdrop(DEPTH, EYE_METERS));
        return new Ground(grid, topology);
    }

    /**
     * The concourse on {@link #CONCOURSE_TIER}, with stairs: the last {@link #STAIR_CELLS} cells of each viaduct and
     * of each stair bridge, next to the concourse, are a ramp up to it. The drop round every deck is void, not
     * ground, so no cliff face is needed: the only walkable cells of different tiers that meet are a ramp's head
     * and the concourse, and any shared edge between walkable cells the tiers would separate is closed, as the
     * elevation brush closes a cliff edge.
     */
    private static void raise(NavigationGrid grid) {
        byte[] tier = new byte[W * H], kind = new byte[W * H];
        double[] rise = new double[W * H];
        for (int y = CONCOURSE[1]; y <= CONCOURSE[3]; y++) {
            for (int x = CONCOURSE[0]; x <= CONCOURSE[2]; x++) tier[y * W + x] = CONCOURSE_TIER;
        }
        for (int[] viaduct : new int[][]{VIADUCT_N, VIADUCT_S}) {
            // Climbing east: the head is the viaduct's east end, beside the concourse.
            for (int x = viaduct[2] - STAIR_CELLS + 1; x <= viaduct[2]; x++) {
                for (int y = viaduct[1]; y <= viaduct[3]; y++) stair(tier, kind, rise, x, y, x - (viaduct[2] - STAIR_CELLS + 1));
            }
        }
        for (int[] stairway : STAIRWAYS) {
            // Climbing west: the head is the bridge's west end, beside the concourse.
            for (int x = stairway[0]; x < stairway[0] + STAIR_CELLS; x++) {
                for (int y = stairway[1]; y <= stairway[3]; y++) stair(tier, kind, rise, x, y, stairway[0] + STAIR_CELLS - 1 - x);
            }
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

    /** A ramp cell climbing from the ground tier to the concourse's, {@code step} cells up from its foot. */
    private static void stair(byte[] tier, byte[] kind, double[] rise, int x, int y, int step) {
        int i = y * W + x;
        tier[i] = CONCOURSE_TIER - 1;
        kind[i] = ElevationMap.RAMP;
        rise[i] = (step + .5) / STAIR_CELLS;
    }

    /** The walkways: the viaducts, the stair bridges and the footbridges. */
    public static List<int[]> walkways() {
        List<int[]> ways = new ArrayList<>(List.of(VIADUCT_N, VIADUCT_S));
        ways.addAll(List.of(STAIRWAYS));
        ways.addAll(List.of(FOOTBRIDGES));
        return ways;
    }

    /** What a cell is laid with, or null where it is void. */
    private static String finish(int x, int y) {
        for (int[] w : walkways()) if (inside(x, y, w)) return "ship-bay.grate-run";
        for (int[] pad : PADS) {
            if (!inside(x, y, pad)) continue;
            boolean rim = x == pad[0] || x == pad[2] || y == pad[1] || y == pad[3];
            return rim ? "apron.keep-clear" : "sky-port.pad-plate";
        }
        // A platform's long edges carry a hatched strip, the edge over the track canyon.
        for (int[] platform : PLATFORMS) {
            if (!inside(x, y, platform)) continue;
            return y == platform[1] || y == platform[3] ? "apron.keep-clear" : "sky-port.deck-plate";
        }
        for (int[] deck : new int[][]{LANDING, CONCOURSE, OUTPOST}) if (inside(x, y, deck)) return "sky-port.deck-plate";
        return null;
    }

    /**
     * The slips down the track canyons between the platforms, open at the platforms' east ends, each berthing by
     * the platforms' heads, each at its own depth above the station's first terrace.
     */
    static List<VoidBackdrop.Slip> slips(float f) {
        List<VoidBackdrop.Slip> slips = new ArrayList<>();
        float[] depths = {58f, 74f, 64f, 82f};
        for (int i = 0; i + 1 < PLATFORM_COUNT; i++) {
            float y = (PLATFORMS[i][3] + 1 + PLATFORMS[i + 1][1]) / 2f;
            slips.add(new VoidBackdrop.Slip(PLATFORM_X1 + 1, y, PLATFORM_X0 + 10, y, depths[i % depths.length] * f));
        }
        return slips;
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
