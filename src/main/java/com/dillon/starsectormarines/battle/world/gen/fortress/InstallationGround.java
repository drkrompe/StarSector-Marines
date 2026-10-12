package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
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
                ctx.topology.setSurface(x, y, 0);
            }
            ctx.topology.setNatureOverlayIndex(x, y, -1);
            // Unpaved margins are maintained clear too: no later ridge or
            // forest may grow across the installation's working ground.
            ctx.markMadeGround(x, y);
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
