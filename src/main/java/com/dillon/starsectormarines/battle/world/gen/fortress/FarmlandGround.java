package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * Cultivated fields: what the open ground round a colony's edge is, painted as
 * plots of furrowed strips so it reads from overhead as farmland rather than as
 * a cleared disc.
 *
 * <p>Cultivated ground, so it is green and turned earth on every world, airless
 * rock included — the same law that keeps a park's lawn green there. A colony
 * grows its food beside itself whatever the planet is made of.
 *
 * <p>Coordinate-based and draws nothing, so painting it moves no other place,
 * force or prop. It changes appearance only: every cell it touches was already
 * open ground and stays exactly as walkable and as see-through as it was.
 */
public final class FarmlandGround {
    private FarmlandGround() {}

    /** Cells along one side of a plot; each plot ploughs its own way. */
    static final int PLOT = 24;

    /** Width of one furrowed strip, crop and turned earth alternating. */
    static final int STRIP = 4;

    /**
     * Paints the open, uncirculated cells of {@code ground} as fields. Roads,
     * buildings, doorways and authored surfaces keep their own treatment.
     */
    public static void dress(GenContext ctx, boolean[][] ground, boolean[][] circulation) {
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        int grass = mapping == null ? 0 : ctx.topology.surfaceIndex(mapping.groundBlockId(GroundKind.GRASS));
        int dirt = mapping == null ? 0 : ctx.topology.surfaceIndex(mapping.groundBlockId(GroundKind.DIRT));
        for (int y = 0; y < ctx.height; y++) {
            for (int x = 0; x < ctx.width; x++) {
                if (!ground[x][y] || circulation[x][y]) continue;
                if (ctx.topology.isWall(x, y) || ctx.topology.isFixture(x, y)
                        || ctx.topology.getBuildingId(x, y) != 0 || ctx.grid.isDoorway(x, y)
                        || ctx.isMadeGround(x, y) || !ctx.grid.isWalkable(x, y)) continue;
                boolean crop = crop(x, y, ctx.seed);
                ctx.topology.setGroundKind(x, y, crop ? GroundKind.GRASS : GroundKind.DIRT);
                ctx.topology.setSurface(x, y, crop ? grass : dirt);
                ctx.topology.setNatureOverlayIndex(x, y, -1);
            }
        }
    }

    /** Whether a cell is crop or turned earth: strips across its plot, the plot's way. */
    static boolean crop(int x, int y, long seed) {
        int plotX = Math.floorDiv(x, PLOT);
        int plotY = Math.floorDiv(y, PLOT);
        long h = seed ^ (plotX * 0x9E3779B97F4A7C15L) ^ (plotY * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        boolean alongX = (h & 1L) == 0;
        int across = alongX ? y : x;
        return Math.floorMod(across, 2 * STRIP) < STRIP;
    }
}
