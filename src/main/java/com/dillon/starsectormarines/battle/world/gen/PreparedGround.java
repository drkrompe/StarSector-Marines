package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Trench laid by any stamper, fort or story map alike. A trench cell stays walkable, takes an
 * earth floor and a trench section joined to its trench neighbours, and the props it leaves are
 * what the fighting-position index reads, so a long enough line is a position a squad can occupy
 * without anything else being told about it.
 */
public final class PreparedGround {

    private PreparedGround() {}

    /** A trench along {@code cells}, each {x, y}, dug through whatever floor is there. Cells that are not walkable are skipped. */
    public static void trenchLine(GenContext ctx, List<int[]> cells) {
        Set<Long> line = new HashSet<>();
        for (int[] cell : cells) line.add(key(cell[0], cell[1]));
        for (int[] cell : cells) {
            if (!ctx.grid.inBounds(cell[0], cell[1]) || !ctx.grid.isWalkable(cell[0], cell[1])) continue;
            ctx.topology.setGroundKind(cell[0], cell[1], GroundKind.DIRT);
            ctx.topology.setSurface(cell[0], cell[1], ctx.topology.surfaceIndex("floors.dirt"));
            ctx.topology.setNatureOverlayIndex(cell[0], cell[1], -1);
            ctx.markMadeGround(cell[0], cell[1]);
            trench(ctx, cell[0], cell[1], line);
        }
    }

    /** A trench section at the world cell, joined to whichever world neighbours are also in {@code trenchCells}. */
    public static void trench(GenContext ctx, int worldX, int worldY, Set<Long> trenchCells) {
        int mask = 0;
        if (trenchCells.contains(key(worldX, worldY + 1))) mask |= 1;
        if (trenchCells.contains(key(worldX + 1, worldY))) mask |= 2;
        if (trenchCells.contains(key(worldX, worldY - 1))) mask |= 4;
        if (trenchCells.contains(key(worldX - 1, worldY))) mask |= 8;
        DoodadDef def = TileRegistry.installed().doodad("doodad.fort-trench-" + mask);
        if (def == null || !ctx.grid.isWalkable(worldX, worldY)) return;
        ctx.grid.setSeeThrough(worldX, worldY, true);
        ctx.topology.setFixture(worldX, worldY, true);
        ctx.doodads.add(new Doodad(worldX, worldY, def));
    }

    /** Whether the world cell is open enough to take a prop: in bounds, walkable, not a doorway, fixture or wall. */
    public static boolean open(GenContext ctx, int x, int y) {
        return ctx.grid.inBounds(x, y) && ctx.grid.isWalkable(x, y) && !ctx.grid.isDoorway(x, y)
                && !ctx.topology.isFixture(x, y) && !ctx.topology.isWall(x, y);
    }

    /**
     * A sandbag piece at the world cell, bags toward world direction {@code toward}, and for a corner a second
     * direction {@code corner} (null for a straight piece). Knee-high cover everyone steps over: the cell stays
     * walkable and the doodad supplies the cover. Refused quietly where the cell is not {@linkplain #open open}.
     */
    public static boolean sandbag(GenContext ctx, int x, int y, int[] toward, int[] corner) {
        if (!open(ctx, x, y)) return false;
        String name;
        if (corner != null && (corner[0] != 0 || corner[1] != 0)) {
            int[] ns = toward[1] != 0 ? toward : corner;
            int[] ew = toward[0] != 0 ? toward : corner;
            name = "corner-" + letter(ns) + letter(ew);
        } else {
            name = String.valueOf(letter(toward));
        }
        DoodadDef def = TileRegistry.installed().doodad("doodad.fort-sandbag-" + name);
        if (def == null) return false;
        ctx.grid.setSeeThrough(x, y, true);
        ctx.topology.setFixture(x, y, true);
        ctx.doodads.add(new Doodad(x, y, def));
        ctx.markMadeGround(x, y);
        return true;
    }

    /**
     * One piece of industrial fence at the world cell, run east-west when {@code horizontal}. Closed to movement, and a
     * destructible cover prop: shells, charges and heavy bodies open it, the same as any other fence in the city.
     */
    public static boolean fence(GenContext ctx, int x, int y, boolean horizontal) {
        if (!open(ctx, x, y)) return false;
        DoodadDef def = TileRegistry.installed().doodad("doodad.industrial-fence-straight-" + (horizontal ? "h" : "v"));
        if (def == null) return false;
        ctx.grid.setWalkable(x, y, false);
        ctx.grid.setSeeThrough(x, y, true);
        ctx.topology.setFixture(x, y, true);
        ctx.doodads.add(new Doodad(x, y, def));
        ctx.markMadeGround(x, y);
        return true;
    }

    /**
     * A one-cell obstacle prop at the world cell — a hedgehog or a crate stack, by doodad id. Closed to movement,
     * open to sight, and a registered cover prop, so it is a barricade: shells and charges break it and a dozer
     * blade pushes it down. Refused quietly where the cell is not {@linkplain #open open} or the art is missing.
     */
    public static boolean obstacle(GenContext ctx, int x, int y, String doodadId) {
        if (!open(ctx, x, y)) return false;
        DoodadDef def = TileRegistry.installed().doodad(doodadId);
        if (def == null) return false;
        ctx.grid.setWalkable(x, y, false);
        ctx.grid.setSeeThrough(x, y, true);
        ctx.topology.setFixture(x, y, true);
        ctx.doodads.add(new Doodad(x, y, def));
        ctx.markMadeGround(x, y);
        return true;
    }

    /**
     * A prop of any footprint at the world cell, its anchor the footprint's low corner after {@code quarterTurns}.
     * What it does to the ground follows from its cover: medium or heavy cover closes its cells to movement, and
     * a prop standing taller than a man ({@code ballisticHalfHeight} of 1 or more: a shed, a container) closes them
     * to sight too; light cover is stepped over like sandbags. A coverless prop is an overhead piece (a crane's span)
     * that lies over whatever is beneath it, so it needs only to be on the map. All or nothing: refused quietly where
     * any cell of a covering prop is not {@linkplain #open open}.
     */
    public static boolean prop(GenContext ctx, int x, int y, DoodadDef def, int quarterTurns) {
        if (def == null) return false;
        Doodad prop = new Doodad(x, y, def, quarterTurns);
        boolean covers = def.cover.level() > 0;
        for (int dy = 0; dy < prop.footprintCellsY; dy++) for (int dx = 0; dx < prop.footprintCellsX; dx++) {
            if (!ctx.grid.inBounds(x + dx, y + dy) || covers && !open(ctx, x + dx, y + dy)) return false;
        }
        boolean solid = def.cover.level() >= DoodadCover.MED.level();
        boolean tall = def.ballisticHalfHeight >= 1f;
        if (covers) for (int dy = 0; dy < prop.footprintCellsY; dy++) for (int dx = 0; dx < prop.footprintCellsX; dx++) {
            if (solid) ctx.grid.setWalkable(x + dx, y + dy, false);
            ctx.grid.setSeeThrough(x + dx, y + dy, !tall);
            ctx.topology.setFixture(x + dx, y + dy, true);
            ctx.markMadeGround(x + dx, y + dy);
        }
        ctx.doodads.add(prop);
        return true;
    }

    /**
     * Ground worked by spades: bare earth, its grass and scrub gone, nothing built. What reads a line of works from a
     * distance is the band of turned earth round it. Refused quietly where the cell is not {@linkplain #open open}.
     */
    public static boolean workedGround(GenContext ctx, int x, int y) {
        if (!open(ctx, x, y)) return false;
        ctx.topology.setGroundKind(x, y, GroundKind.DIRT);
        ctx.topology.setSurface(x, y, ctx.topology.surfaceIndex("floors.dirt"));
        ctx.topology.setNatureOverlayIndex(x, y, -1);
        ctx.markMadeGround(x, y);
        return true;
    }

    private static char letter(int[] dir) {
        if (dir[1] > 0) return 'n';
        if (dir[1] < 0) return 's';
        return dir[0] > 0 ? 'e' : 'w';
    }

    /** The key {@link #trench} reads its neighbours by. */
    public static long key(int x, int y) {
        return (long) x << 32 | (y & 0xffffffffL);
    }
}
