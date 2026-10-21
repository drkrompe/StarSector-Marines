package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.List;

/** The installation owns its outdoor surfaces, including ground already open before packing. */
public final class InstallationGround {
    private InstallationGround() {}

    public static void dress(GenContext ctx, boolean[][] ground, boolean[][] circulation,
                             List<RoomPacker.Placed> rooms) {
        boolean[][] building = new boolean[ctx.width][ctx.height];
        boolean[][] apron = new boolean[ctx.width][ctx.height];
        for (var room : rooms) {
            for (int[] cell : room.shape().filled()) {
                mark(building, room.originX() + cell[0], room.originY() + cell[1]);
            }
            // Follow the actual ring, not the bounding box of an L-shaped building.
            // Walkable gaps in that ring can be yard; aperture hints and door tags
            // below protect the cells that really belong to the building.
            for (int[] cell : room.shape().wall()) {
                int x = room.originX() + cell[0], y = room.originY() + cell[1];
                for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++)
                    mark(apron, x + dx, y + dy);
            }
        }
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        SurfacePalette planet = profile == null ? SurfacePalette.ROCK : profile.surface();
        int[] concrete = surfaces(ctx, Material.CONCRETE);
        int[] asphalt = surfaces(ctx, Material.ASPHALT);
        GroundKind margin = planet.inlandGround();
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        int marginSurface = mapping == null ? 0 : ctx.topology.surfaceIndex(mapping.groundBlockId(margin));
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++) {
            if ((!ground[x][y] && !circulation[x][y]) || building[x][y]
                    || ctx.topology.isWall(x, y) || ctx.topology.isFixture(x, y)
                    || ctx.topology.hasBuildingKindHint(x, y) || ctx.grid.isDoorway(x, y)
                    || ctx.isMadeGround(x, y) || !ctx.grid.isWalkable(x, y)) continue;
            // Kept roads and the packer's cut access paths remain continuous.
            // Coordinate-based wear never consumes the layout/furnishing RNG.
            GroundKind old = ctx.topology.getGroundKind(x, y);
            int age = RoadRasterizer.age(.4, ctx.seed, x, y);
            if (circulation[x][y]) {
                ctx.topology.setSurface(x, y, asphalt[age]);
            } else if (apron[x][y] || old == GroundKind.STONE || old == GroundKind.STREET
                    || old == GroundKind.SIDEWALK || old == GroundKind.COURTYARD) {
                ctx.topology.setSurface(x, y, concrete[age]);
            } else {
                ctx.topology.setGroundKind(x, y, margin);
                ctx.topology.setSurface(x, y, marginSurface);
            }
            ctx.topology.setNatureOverlayIndex(x, y, -1);
            // Explicit surfaces exclude later landscape on these exact cells.
            // A made-ground reservation would also clear a two-cell halo outside
            // the wall, moving obstacles and the mission's selected landing zone.
        }
    }

    /**
     * Bare ground on a walled place's glacis: the strip outside its wall kept
     * clear of building, laid as the planet's own earth so it reads as cleared
     * ground rather than as the town's pavement. Road across it stays road.
     */
    public static void glacis(GenContext ctx, boolean[][] strip, boolean[][] road) {
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        SurfacePalette planet = profile == null ? SurfacePalette.ROCK : profile.surface();
        GroundKind ground = planet.inlandGround();
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        int surface = mapping == null ? 0 : ctx.topology.surfaceIndex(mapping.groundBlockId(ground));
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++) {
            if (!strip[x][y] || road[x][y] || ctx.topology.isWall(x, y) || ctx.topology.isFixture(x, y)
                    || !ctx.grid.isWalkable(x, y)) continue;
            ctx.topology.setGroundKind(x, y, ground);
            ctx.topology.setSurface(x, y, surface);
            ctx.topology.setNatureOverlayIndex(x, y, -1);
        }
    }

    private static int[] surfaces(GenContext ctx, Material material) {
        int[] out = new int[3];
        for (int age = 0; age < out.length; age++) out[age] = ctx.topology.surfaceIndex(material.block(age));
        return out;
    }

    private static void mark(boolean[][] mask, int x, int y) {
        if (x >= 0 && y >= 0 && x < mask.length && y < mask[0].length) mask[x][y] = true;
    }
}
