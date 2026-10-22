package com.dillon.starsectormarines.battle.world.gen.rural;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
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
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;

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
        int[] asphalt = new int[3];
        for (int age = 0; age < 3; age++) asphalt[age] = ctx.topology.surfaceIndex(Material.ASPHALT.block(age));
        for (int y = 0; y < ctx.height; y++) {
            for (int x = 0; x < ctx.width; x++) {
                if (!layout.inCountry(x, y) || !open(ctx, x, y)) continue;
                GroundKind kind;
                int surface;
                Road road = layout.roadAt(x, y);
                if (road != null && road.grade() == Grade.COUNTRY) {
                    // Paved ground, the way a kept precinct road is: no dust off it.
                    kind = GroundKind.STREET;
                    surface = asphalt[RoadRasterizer.age(.4, ctx.seed, x, y)];
                } else if (road != null || layout.farmsteadAt(x, y) != null) {
                    // A track, the farm road, or a farmyard trodden bare.
                    kind = GroundKind.DIRT;
                    surface = dirt;
                } else {
                    kind = plotGround(layout.plotAt(x, y), x, y);
                    surface = kind == GroundKind.DIRT ? dirt : kind == GroundKind.SAND ? sand : grass;
                }
                ctx.topology.setGroundKind(x, y, kind);
                ctx.topology.setSurface(x, y, surface);
                int plant = -1;
                if (hedge.length > 0 && kind == GroundKind.GRASS && layout.hedgeAt(x, y)) {
                    plant = hedge[(int) Math.floorMod(hash(ctx.seed, x, y), (long) hedge.length)];
                }
                ctx.topology.setNatureOverlayIndex(x, y, plant);
            }
        }
        raiseFarmsteads(ctx, layout);
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
    static GroundKind plotGround(Plot plot, int x, int y) {
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
        };
    }

    /**
     * The roads as straight routes of constant width, flush at both ends, higher
     * grades first so a junction is the higher road's surface.
     */
    public static List<Route> routes(RuralLayout layout, long seed) {
        List<Route> out = new ArrayList<>();
        for (Road road : layout.roads()) {
            Point a, z;
            if (road.alongX()) {
                double cy = (road.y0() + road.y1()) / 2.0;
                a = new Point(road.x0() - .5, cy);
                z = new Point(road.x1() + .5, cy);
            } else {
                double cx = (road.x0() + road.x1()) / 2.0;
                a = new Point(cx, road.y0() - .5);
                z = new Point(cx, road.y1() + .5);
            }
            Grade grade = road.grade();
            double wear = grade == Grade.COUNTRY ? .4 : .6;
            out.add(new Route("rural-" + grade.name().toLowerCase() + "-" + out.size(), List.of(a, z),
                    grade.width, 0, wear, seed, grade.material, EndCap.BUTT));
        }
        return out;
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
