package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.road.GeneratedRoads;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;

/**
 * Finish a generated city's carriageways with the spline pavement after
 * placement; ground semantics and clearance stay authoritative.
 *
 * <p>Two networks feed one sweep. A precinct map's kept service roads are
 * already asphalt and keep the treatment they had. The ordinary city street —
 * {@link GroundKind#STREET} with no surface of its own, the urban paving — gains
 * asphalt over its carriageway: every such cell except the kerb strip the
 * renderer draws wherever a wall or building is within one cell. The kerb, the
 * pedestrian frames, plazas wider than {@link #CITY_MAX_BREADTH} and lanes
 * narrower than a sweep keep the paving, which also shows through in the
 * corners a round end or a junction turn leaves uncovered.
 *
 * <p>This is a finish, not a repaving: no cell's ground kind, surface,
 * walkability or tag other than {@link Tag#ROAD_EDGE_BLEND} changes, and no
 * random number is drawn. {@code -Dbattle.mapgen.cityRoads=false} is the
 * control: the city keeps its paving and only precinct roads are swept.
 */
public final class RoadSurfaceStage implements GenStage {
    /**
     * Wider than any street the city lays (a primary trunk is seven) and narrower
     * than a yard or square. A band broader than this is open paving.
     */
    static final int CITY_MAX_BREADTH = 9;

    static boolean cityRoadsEnabled() {
        return Boolean.parseBoolean(System.getProperty("battle.mapgen.cityRoads", "true"));
    }

    @Override public void run(GenContext ctx) {
        if (ctx.topology.roadSurface() != null) return;
        int[][] owner = ctx.get(BspKeys.PRECINCT_ROAD);
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
        boolean precinct = owner != null && claim != null;
        boolean city = cityRoadsEnabled();
        if (!precinct && !city) return;
        var precincts = ctx.get(BspKeys.PRECINCTS);
        boolean[][] road = new boolean[ctx.width][ctx.height];
        boolean[][] street = new boolean[ctx.width][ctx.height];
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++) {
            road[x][y] = precinct && owner[x][y] != GrownTrunkPlan.UNOWNED
                    && (claim[x][y] == GrownTrunkPlan.UNOWNED || precincts == null
                    || precincts.precincts().get(claim[x][y]).isProgrammed());
            street[x][y] = city && !road[x][y] && carriageway(ctx, x, y);
        }
        boolean[][] swept = new boolean[ctx.width][ctx.height];
        boolean anyStreet = false;
        for (int x = 0; x < ctx.width; x++) for (int y = 0; y < ctx.height; y++) {
            swept[x][y] = road[x][y] || street[x][y];
            anyStreet |= street[x][y];
        }
        // Precinct-only maps keep their own bound, so the control reproduces them.
        var routes = GeneratedRoads.routes(swept, ctx.seed,
                anyStreet ? CITY_MAX_BREADTH : GeneratedRoads.MAX_BREADTH);
        if (routes.isEmpty()) return;
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        var margin = (profile == null ? SurfacePalette.ROCK : profile.surface()).inlandGround();
        var mapping = GenMappingRegistry.installed();
        int nativeSurface = mapping == null ? 0 : ctx.topology.surfaceIndex(mapping.groundBlockId(margin));
        var allowed = new RoadSurface.Underlay[ctx.width][ctx.height];
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++) {
            var kind = ctx.topology.getGroundKind(x, y);
            if (street[x][y]) {
                // The city's own paving is the underlay: it shows where the sweep does not reach.
                allowed[x][y] = new RoadSurface.Underlay(kind, ctx.topology.getSurface(x, y), false, false);
                continue;
            }
            if (!precinct || !outdoorOpen(ctx, x, y) || kind == GroundKind.STRIPED) continue;
            String id = ctx.topology.getSurfaceId(x, y);
            if (id == null) continue;
            boolean asphalt = road[x][y] && id.startsWith("pavement.asphalt.");
            // Junction turns may borrow only the existing concrete shoulder.
            // Natural terrain, props, authored pads and building thresholds keep ownership.
            if (!asphalt && !(id.startsWith("pavement.concrete.") && adjacent(road, x, y))) continue;
            int underlay = ctx.topology.getSurface(x, y);
            if (asphalt) underlay = claim[x][y] == GrownTrunkPlan.UNOWNED ? nativeSurface
                    : ctx.topology.surfaceIndex(Material.CONCRETE.block(RoadRasterizer.age(.4, ctx.seed, x, y)));
            // Retain the semantic kind too: this pass cannot reroll defense placement,
            // landscape, spawns, navigation costs, or any later combat behavior.
            allowed[x][y] = new RoadSurface.Underlay(kind, underlay, false, false);
        }
        var surface = RoadSurface.generated(routes, ctx.topology, allowed);
        ctx.topology.setRoadSurface(surface);
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++)
            if (surface.underlay(x, y) != null) ctx.topology.setTag(x, y, Tag.ROAD_EDGE_BLEND, true);
    }

    /** Open outdoor ground a finish may draw over: no structure, threshold, pad or interior. */
    private static boolean outdoorOpen(GenContext ctx, int x, int y) {
        var kind = ctx.topology.getGroundKind(x, y);
        return ctx.grid.isWalkable(x, y) && !ctx.topology.isWall(x, y) && !ctx.topology.isFixture(x, y)
                && !ctx.topology.hasBuildingKindHint(x, y) && !ctx.grid.isDoorway(x, y)
                && !ctx.isMadeGround(x, y) && kind != GroundKind.INDOOR;
    }

    /**
     * A city carriageway cell: an unsurfaced street with no wall or building in
     * its eight neighbours. The renderer draws exactly those neighbours as kerb
     * ({@code GroundTileSelector.isSidewalkCell}), so the asphalt stops at the kerb.
     */
    static boolean carriageway(GenContext ctx, int x, int y) {
        if (ctx.topology.getGroundKind(x, y) != GroundKind.STREET || ctx.topology.getSurface(x, y) != 0
                || !outdoorOpen(ctx, x, y)) return false;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int nx = x + dx, ny = y + dy;
            if ((dx != 0 || dy != 0) && ctx.topology.inBounds(nx, ny)
                    && (ctx.topology.isWall(nx, ny) || ctx.topology.hasBuildingKindHint(nx, ny))) return false;
        }
        return true;
    }

    private static boolean adjacent(boolean[][] road, int x, int y) {
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx >= 0 && ny >= 0 && nx < road.length && ny < road[0].length && road[nx][ny]) return true;
        }
        return false;
    }
}
