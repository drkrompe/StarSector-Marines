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

/** Finish generated service roads after placement; ground semantics and clearance stay authoritative. */
public final class PrecinctRoadSurfaceStage implements GenStage {
    @Override public void run(GenContext ctx) {
        int[][] owner = ctx.get(BspKeys.PRECINCT_ROAD);
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
        if (owner == null || claim == null || ctx.topology.roadSurface() != null) return;
        var precincts = ctx.get(BspKeys.PRECINCTS);
        boolean[][] road = new boolean[ctx.width][ctx.height];
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++)
            road[x][y] = owner[x][y] != GrownTrunkPlan.UNOWNED
                    && (claim[x][y] == GrownTrunkPlan.UNOWNED || precincts == null
                    || precincts.precincts().get(claim[x][y]).isProgrammed());
        var routes = GeneratedRoads.routes(road, ctx.seed);
        if (routes.isEmpty()) return;
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        var margin = (profile == null ? SurfacePalette.ROCK : profile.surface()).inlandGround();
        var mapping = GenMappingRegistry.installed();
        int nativeSurface = mapping == null ? 0 : ctx.topology.surfaceIndex(mapping.groundBlockId(margin));
        var allowed = new RoadSurface.Underlay[ctx.width][ctx.height];
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++) {
            var kind = ctx.topology.getGroundKind(x, y);
            if (!ctx.grid.isWalkable(x, y) || ctx.topology.isWall(x, y) || ctx.topology.isFixture(x, y)
                    || ctx.topology.hasBuildingKindHint(x, y) || ctx.grid.isDoorway(x, y)
                    || ctx.isMadeGround(x, y) || kind == GroundKind.INDOOR || kind == GroundKind.STRIPED) continue;
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

    private static boolean adjacent(boolean[][] road, int x, int y) {
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx >= 0 && ny >= 0 && nx < road.length && ny < road[0].length && road[nx][ny]) return true;
        }
        return false;
    }
}
