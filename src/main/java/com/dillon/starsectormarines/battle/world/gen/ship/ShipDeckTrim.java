package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;

/** Visual trim follows existing structure and material regions; never reserves or obstructs deck. */
public final class ShipDeckTrim {
    private static final String KIT = "ship-floor-trim.";
    // Image top is world +Y; positive sprite turns are counterclockwise on screen.
    private static final int[][] DIRECTIONS = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    private ShipDeckTrim() {}

    public static void apply(GenContext ctx) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        DoodadDef edge = registry.doodad(KIT + "edge"), binding = registry.doodad(KIT + "binding");
        DoodadDef rib = registry.doodad(KIT + "rib"), access = registry.doodad(KIT + "access");
        if (edge == null || binding == null || rib == null || access == null) return;
        // Reapplying the presentation stage replaces only this kit's decorative overlays.
        ctx.doodads.removeIf(d -> d.definitionId() != null && d.definitionId().startsWith(KIT));
        CellTopology t = ctx.topology;
        List<Doodad> trim = new ArrayList<>();
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++) {
            String surface = t.getSurfaceId(x, y);
            if (floor(ctx, x, y)) {
                for (int turn = 0; turn < 4; turn++) {
                    int nx = x + DIRECTIONS[turn][0], ny = y + DIRECTIONS[turn][1];
                    if (!inside(ctx, nx, ny)) continue;
                    if (t.isWall(nx, ny)) trim.add(new Doodad(x, y, edge, turn));
                    else if (inset(surface) && floor(ctx, nx, ny)
                            && !surface.equals(t.getSurfaceId(nx, ny)) && !ctx.grid.isDoorway(x, y))
                        trim.add(new Doodad(x, y, binding, turn));
                }
                // One flush cover per long passage run, never on a threshold or beneath furniture.
                if ((KIT + "passage").equals(surface) && !t.isFixture(x, y)
                        && !ctx.grid.isDoorway(x, y)
                        && ((x % 12 == 6 && wall(ctx, x, y-2))
                            || (y % 12 == 6 && wall(ctx, x-2, y)))
                        && clearAround(ctx, x, y)) trim.add(new Doodad(x, y, access));
            } else if (t.isWall(x, y) && "ship.neutral-bulkhead".equals(surface)) {
                boolean horizontal = wall(ctx, x-1, y) && wall(ctx, x+1, y)
                        && deck(ctx, x, y-1) && deck(ctx, x, y+1)
                        && (floor(ctx, x, y-1) || floor(ctx, x, y+1));
                boolean vertical = wall(ctx, x, y-1) && wall(ctx, x, y+1)
                        && deck(ctx, x-1, y) && deck(ctx, x+1, y)
                        && (floor(ctx, x-1, y) || floor(ctx, x+1, y));
                boolean jambH = door(ctx, x-1, y) || door(ctx, x+1, y);
                boolean jambV = door(ctx, x, y-1) || door(ctx, x, y+1);
                // Outer hull faces blend into solid hull; full-width ribs there read as projecting spikes.
                if ((horizontal && x % 8 == 0) || jambH) trim.add(new Doodad(x, y, rib, 0));
                else if ((vertical && y % 8 == 0) || jambV) trim.add(new Doodad(x, y, rib, 1));
            }
        }
        // All floor accents must remain beneath furniture, including walkable stools and stock layers.
        ctx.doodads.addAll(0, trim);
    }

    private static boolean inset(String surface) {
        return (KIT + "lounge-inset").equals(surface) || (KIT + "clinical-aisle").equals(surface)
                || (KIT + "service-pad").equals(surface);
    }
    private static boolean clearAround(GenContext ctx, int x, int y) {
        for (int[] d : DIRECTIONS) {
            int nx = x + d[0], ny = y + d[1];
            if (!floor(ctx, nx, ny) || ctx.grid.isDoorway(nx, ny)
                    || ctx.topology.isFixture(nx, ny)) return false;
        }
        return true;
    }
    private static boolean door(GenContext ctx, int x, int y) {
        return floor(ctx, x, y) && ctx.grid.isDoorway(x, y);
    }
    private static boolean wall(GenContext ctx, int x, int y) {
        return inside(ctx, x, y) && ctx.topology.isWall(x, y);
    }
    private static boolean floor(GenContext ctx, int x, int y) {
        if (!deck(ctx, x, y)) return false;
        String surface = ctx.topology.getSurfaceId(x, y);
        return surface != null && surface.startsWith(KIT);
    }
    private static boolean deck(GenContext ctx, int x, int y) {
        return inside(ctx, x, y) && !ctx.topology.isWall(x, y)
                && ctx.topology.getGroundKind(x, y) != GroundKind.VOID;
    }
    private static boolean inside(GenContext ctx, int x, int y) {
        return x >= 0 && y >= 0 && x < ctx.width && y < ctx.height;
    }
}
