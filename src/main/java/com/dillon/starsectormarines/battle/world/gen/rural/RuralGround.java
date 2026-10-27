package com.dillon.starsectormarines.battle.world.gen.rural;

import com.dillon.starsectormarines.battle.world.gen.SurfaceField;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.EndCap;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Route;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Farmstead;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Grade;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Plot;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Road;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Use;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Paints a {@link RuralLayout} onto the map: each plot by its use, every road
 * band as the bare ground its surface is laid over, and the roads themselves as
 * routes for the road finish to sweep.
 *
 * <p>Cultivated ground, so it is green and turned earth on every world, airless
 * rock included, the same law that keeps a park's lawn green there. A colony
 * grows its food beside itself whatever the planet is made of.
 *
 * <p>Coordinate-based and draws nothing. Fields, roads, yards and hedges change
 * appearance only: every cell they touch was open ground and stays exactly as
 * walkable and as see-through as it was. The farmhouses and barns are the one
 * structural thing in the country, ordinary stamped buildings standing in their
 * yards well outside the landing's field of fire. Buildings, walls, doorways
 * and authored surfaces already on the map keep their own treatment.
 */
public final class RuralGround {
    private RuralGround() {}

    /** Unploughed margin round each plot, where the drill turns. */
    static final int HEADLAND = 1;
    /** One crop row and the furrow beside it. */
    static final int ROW_PITCH = 2;
    /** Grain stands solid; a tramline crosses it every this many rows. */
    static final int TRAMLINE_PITCH = 6;

    public static void dress(GenContext ctx, RuralLayout layout) {
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        int grass = surface(ctx, mapping, GroundKind.GRASS);
        int dirt = surface(ctx, mapping, GroundKind.DIRT);
        int sand = surface(ctx, mapping, GroundKind.SAND);
        int[] hedge = hedgePlants();
        SurfaceField wild = SurfaceField.of(ctx);
        int[] asphalt = new int[3];
        for (int age = 0; age < 3; age++) asphalt[age] = ctx.topology.surfaceIndex(Material.ASPHALT.block(age));
        for (int y = 0; y < ctx.height; y++) {
            for (int x = 0; x < ctx.width; x++) {
                if (!layout.inCountry(x, y) || !open(ctx, x, y)) continue;
                GroundKind kind;
                int surface;
                boolean worked = false;
                Road road = layout.roadAt(x, y);
                if (road != null && road.grade() == Grade.COUNTRY) {
                    // Paved ground, the way a kept precinct road is: no dust off it.
                    kind = GroundKind.STREET;
                    surface = asphalt[RoadRasterizer.age(.4, ctx.seed, x, y)];
                } else if (road != null || layout.farmsteadAt(x, y) != null) {
                    // A track, the farm road, or a farmyard trodden bare.
                    kind = GroundKind.DIRT;
                    surface = dirt;
                } else if (layout.plotAt(x, y) == null || layout.plotAt(x, y).use() == Use.WILD) {
                    // Ground nobody works is the planet's own, as unclaimed land is.
                    kind = wild.at(x, y).inlandGround();
                    surface = 0;
                } else {
                    kind = plotGround(layout.plotAt(x, y), x, y);
                    surface = kind == GroundKind.DIRT ? dirt : kind == GroundKind.SAND ? sand : grass;
                    worked = true;
                }
                ctx.topology.setGroundKind(x, y, kind);
                ctx.topology.setSurface(x, y, surface);
                int plant = -1;
                if (hedge.length > 0 && kind == GroundKind.GRASS && layout.hedgeAt(x, y)) {
                    plant = hedge[(int) Math.floorMod(hash(ctx.seed, x, y), (long) hedge.length)];
                }
                ctx.topology.setNatureOverlayIndex(x, y, plant);
                // A worked field is made ground: no hill or rock formation is raised on it.
                if (worked) ctx.markMadeGround(x, y);
            }
        }
        raiseFarmsteads(ctx, layout);
    }

    /**
     * Lays and paints the fields a colony works on its other sides
     * ({@link TownFields}): on unclaimed open ground outside the surveyed
     * country, fronting the town's places and its roads. {@code claim} and
     * {@code road} are the precinct claim and road-owner grids. Like the
     * country's fields they change appearance only; a passable prop the
     * hinterland scatter left wholly inside one is cleared off it, and the
     * field is made ground so nothing is raised on it later.
     */
    public static List<Plot> townFields(GenContext ctx, RuralLayout layout, int[][] claim, int[][] road) {
        int w = ctx.width, h = ctx.height;
        boolean[][] open = new boolean[w][h], town = new boolean[w][h], roads = new boolean[w][h];
        FieldOfFire field = layout.field();
        long clear = field == null ? 0 : field.radius() + RuralLayout.HEDGE_CLEARANCE;
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        // Ground the landscape held for its ridges is not ploughed: a field is made ground, and no ridge rises on one.
        boolean[][] held = ctx.get(BspKeys.HELD_GROUND);
        // Nor is ground the mission's layout states up front, open or held for terrain: its set pieces stand there.
        boolean[][] sites = plan == null ? null : plan.layout().keepClear(w, h);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (layout.inCountry(x, y)) continue;
                int owner = claim[x][y];
                roads[x][y] = road[x][y] != GrownTrunkPlan.UNOWNED;
                // Natural ground nothing was built on is open, inside a grown
                // place's claim as much as outside every claim; a programmed
                // place is built later and keeps all of its own.
                boolean programmed = owner != GrownTrunkPlan.UNOWNED && plan != null
                        && owner < plan.precincts().size() && plan.precincts().get(owner).isProgrammed();
                boolean natural = !roads[x][y] && !programmed && open(ctx, x, y)
                        && NATURAL.contains(ctx.topology.getGroundKind(x, y)) && !nearField(field, clear, x, y)
                        && (held == null || !held[x][y]) && (sites == null || !sites[x][y]);
                open[x][y] = natural;
                town[x][y] = owner != GrownTrunkPlan.UNOWNED && !natural && !roads[x][y];
            }
        }
        List<Plot> fields = TownFields.plan(w, h, open, town, roads, ctx.seed);
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        int grass = surface(ctx, mapping, GroundKind.GRASS);
        int dirt = surface(ctx, mapping, GroundKind.DIRT);
        int sand = surface(ctx, mapping, GroundKind.SAND);
        int[] hedge = hedgePlants();
        for (Plot plot : fields) {
            for (int x = plot.x0(); x <= plot.x1(); x++) {
                for (int y = plot.y0(); y <= plot.y1(); y++) {
                    GroundKind kind = plotGround(plot, x, y);
                    ctx.topology.setGroundKind(x, y, kind);
                    ctx.topology.setSurface(x, y, kind == GroundKind.DIRT ? dirt : kind == GroundKind.SAND ? sand : grass);
                    int plant = -1;
                    if (plot.hedged() && hedge.length > 0 && kind == GroundKind.GRASS && fieldHedge(plot, x, y)) {
                        plant = hedge[(int) Math.floorMod(hash(ctx.seed, x, y), (long) hedge.length)];
                    }
                    ctx.topology.setNatureOverlayIndex(x, y, plant);
                    ctx.markMadeGround(x, y);
                }
            }
        }
        ctx.doodads.removeIf(d -> fields.stream().anyMatch(p -> p.contains(d.cellX, d.cellY)
                && p.contains(d.cellX + d.footprintCellsX - 1, d.cellY + d.footprintCellsY - 1)));
        return fields;
    }

    /** Ground a field may be laid on: the planet's own surface, not anything made or paved. */
    private static final Set<GroundKind> NATURAL = EnumSet.of(GroundKind.GRASS, GroundKind.DIRT,
            GroundKind.STONE, GroundKind.SAND, GroundKind.SNOW, GroundKind.RUBBLE);

    private static boolean nearField(FieldOfFire field, long clear, int x, int y) {
        if (field == null) return false;
        long dx = x - field.centerX(), dy = y - field.centerY();
        return dx * dx + dy * dy <= clear * clear;
    }

    /** The outer headland of a field, round all four sides, less a gateway in the middle of each. */
    static boolean fieldHedge(Plot plot, int x, int y) {
        boolean side = x == plot.x0() || x == plot.x1(), end = y == plot.y0() || y == plot.y1();
        if (side && !end) return !inGateway(y, plot.y0(), plot.y1());
        if (end && !side) return !inGateway(x, plot.x0(), plot.x1());
        return side;
    }

    private static boolean inGateway(int at, int lo, int hi) {
        int mid = (lo + hi) / 2;
        return at >= mid - RuralLayout.GATEWAY / 2 && at < mid - RuralLayout.GATEWAY / 2 + RuralLayout.GATEWAY;
    }

    /**
     * Stands each farm's house and barn in its yard, where the yard is still
     * open ground; a farm whose ground something else took keeps its bare yard.
     */
    static void raiseFarmsteads(GenContext ctx, RuralLayout layout) {
        for (Farmstead farm : layout.farmsteads()) {
            boolean clear = true;
            for (int x = farm.yardX0(); x <= farm.yardX1() && clear; x++)
                for (int y = farm.yardY0(); y <= farm.yardY1() && clear; y++)
                    clear = ctx.topology.inBounds(x, y) && open(ctx, x, y);
            if (!clear) continue;
            BuildingStamp.FARMHOUSE.place(ctx, farm.houseX(), farm.houseY(), farm.turns());
            BuildingStamp.BARN.place(ctx, farm.barnX(), farm.barnY(), farm.turns());
        }
    }

    /**
     * The plants a hedge is grown from, weighted by repetition: the broad
     * rosette most, scrub and tall grass between. Passable and coverless, so a
     * hedge is a boundary to the eye and nothing else.
     */
    private static int[] hedgePlants() {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return new int[0];
        String[] ids = {"nature.shrub-1", "nature.shrub-1", "nature.shrub-1", "nature.shrub-2", "nature.shrub-2", "nature.tuft-3"};
        List<Integer> out = new ArrayList<>();
        for (String id : ids) if (registry.has(id)) out.add(registry.indexOf(id));
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static long hash(long seed, int x, int y) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 29);
    }

    /** What one cell of a plot grows; outside every plot, rough grass. */
    public static GroundKind plotGround(Plot plot, int x, int y) {
        if (plot == null) return GroundKind.GRASS;
        boolean headland = x - plot.x0() < HEADLAND || plot.x1() - x < HEADLAND
                || y - plot.y0() < HEADLAND || plot.y1() - y < HEADLAND;
        if (headland) return GroundKind.GRASS;
        int across = plot.rowsAlongX() ? y - plot.y0() : x - plot.x0();
        return switch (plot.use()) {
            case ROW_CROP -> Math.floorMod(across, ROW_PITCH) == 0 ? GroundKind.GRASS : GroundKind.DIRT;
            case GRAIN -> Math.floorMod(across, TRAMLINE_PITCH) == 0 ? GroundKind.DIRT : GroundKind.SAND;
            case PASTURE -> GroundKind.GRASS;
            case FALLOW -> GroundKind.DIRT;
            case WILD -> GroundKind.GRASS;
        };
    }

    /** Cells over which the country road narrows to the street it carries into town. */
    static final int TAPER = 4;

    /**
     * The roads as straight routes of constant width, flush at both ends, higher
     * grades first so a junction is the higher road's surface.
     *
     * <p>The country road is broader than most streets it carries on. Where one
     * of the {@code streets} (the town's swept routes) starts at the country's
     * edge on the country road's line, the road narrows to that street's asphalt
     * over {@link #TAPER} cells short of the farm road and crosses it at the
     * street's width, so the two meet flush at the town edge instead of in a
     * square step.
     */
    public static List<Route> routes(RuralLayout layout, List<Route> streets, long seed) {
        List<Route> out = new ArrayList<>();
        for (Road road : layout.roads()) {
            boolean alongX = road.alongX();
            double centre = alongX ? (road.y0() + road.y1()) / 2.0 : (road.x0() + road.x1()) / 2.0;
            double lo = (alongX ? road.x0() : road.y0()) - .5, hi = (alongX ? road.x1() : road.y1()) + .5;
            Grade grade = road.grade();
            double wear = grade == Grade.COUNTRY ? .4 : .6;
            String id = "rural-" + grade.name().toLowerCase() + "-";
            Route street = grade == Grade.COUNTRY ? arrival(layout, road, streets) : null;
            if (street == null) {
                out.add(new Route(id + out.size(), List.of(at(alongX, lo, centre), at(alongX, hi, centre)),
                        grade.width, 0, wear, seed, grade.material, EndCap.BUTT));
                continue;
            }
            // The town end, which way is towards it, and where the farm road along it begins.
            boolean townHi = !layout.inCountry(alongX ? road.x1() + 1 : road.x0(), alongX ? road.y0() : road.y1() + 1);
            int dir = townHi ? 1 : -1;
            double edge = townHi ? hi : lo, farm = edge - dir * Grade.FARM.width, taper = farm - dir * TAPER;
            double narrow = street.width(), to = alongX ? street.points().get(0).y() : street.points().get(0).x();
            out.add(new Route(id + out.size(), List.of(at(alongX, townHi ? lo : hi, centre), at(alongX, taper, centre)),
                    grade.width, 0, wear, seed, grade.material, EndCap.BUTT));
            // Two strokes of the street's width, one flush with each of the road's
            // edges where the taper starts and both on the street at the farm road:
            // together, a trapezoid from the road's width to the street's. Their
            // ends are round: a flush end on a slanted stroke is cut square to the
            // slant and leaves a notch, where a round one lies wholly inside the
            // road behind it and the street ahead.
            double shift = (grade.width - narrow) / 2;
            for (int side : new int[]{-1, 1})
                out.add(new Route(id + out.size(), List.of(at(alongX, taper, centre + side * shift),
                        at(alongX, farm, to)), narrow, 0, wear, seed, grade.material, EndCap.ROUND));
            out.add(new Route(id + out.size(), List.of(at(alongX, farm, to), at(alongX, edge, to)),
                    narrow, 0, wear, seed, grade.material, EndCap.BUTT));
        }
        return out;
    }

    /**
     * The street a country road carries into town: a straight swept route along
     * the road's axis, narrower than the road, starting on the country's edge
     * with its centre line inside the road. {@code null} when there is none.
     */
    private static Route arrival(RuralLayout layout, Road road, List<Route> streets) {
        boolean alongX = road.alongX();
        boolean townHi = !layout.inCountry(alongX ? road.x1() + 1 : road.x0(), alongX ? road.y0() : road.y1() + 1);
        double edge = townHi ? (alongX ? road.x1() : road.y1()) + .5 : (alongX ? road.x0() : road.y0()) - .5;
        int across0 = alongX ? road.y0() : road.x0(), across1 = alongX ? road.y1() : road.x1();
        for (Route r : streets) {
            if (r.points().size() != 2 || r.width() >= road.grade().width) continue;
            Point a = r.points().get(0), b = r.points().get(1);
            double c = alongX ? a.y() : a.x();
            if ((alongX ? b.y() : b.x()) != c || c < across0 || c > across1) continue;
            double s = alongX ? a.x() : a.y(), t = alongX ? b.x() : b.y();
            if (Math.abs(Math.min(s, t) - edge) < 1e-6 || Math.abs(Math.max(s, t) - edge) < 1e-6) return r;
        }
        return null;
    }

    private static Point at(boolean alongX, double along, double across) {
        return alongX ? new Point(along, across) : new Point(across, along);
    }

    /** The ground a road band's surface is laid over, for the road finish. */
    public static RoadSurface.Underlay underlay(GenContext ctx, RuralLayout layout, int x, int y) {
        Road road = layout.roadAt(x, y);
        if (road == null || !open(ctx, x, y)) return null;
        int dirt = surface(ctx, GenMappingRegistry.installed(), GroundKind.DIRT);
        return new RoadSurface.Underlay(ctx.topology.getGroundKind(x, y), dirt, false, false);
    }

    private static int surface(GenContext ctx, GenMappingRegistry mapping, GroundKind kind) {
        return mapping == null ? 0 : ctx.topology.surfaceIndex(mapping.groundBlockId(kind));
    }

    private static boolean open(GenContext ctx, int x, int y) {
        return !ctx.topology.isWall(x, y) && !ctx.topology.isFixture(x, y)
                && ctx.topology.getBuildingId(x, y) == 0 && !ctx.grid.isDoorway(x, y)
                && !ctx.isMadeGround(x, y) && ctx.grid.isWalkable(x, y);
    }
}
