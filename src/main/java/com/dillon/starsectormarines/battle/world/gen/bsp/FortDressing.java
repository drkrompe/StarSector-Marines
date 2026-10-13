package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Placement;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.Set;

/**
 * Draws a fort's earthworks and yard furniture from the existing doodad catalogue, in the fort's local frame: sandbag
 * lines and rings (walkable heavy cover, the same pieces defence posts and Thunder Run use), industrial fence for wire
 * (closed to movement, breachable), earth floor for trench and pit, and plant and stores props in the yards.
 *
 * <p>Every placement is refused quietly where the ground is not open: a fort is stamped into a city that exists, and a
 * missing sandbag is a gap in a line, not a broken map. Directions are given in the local frame and turned with the
 * placement, so a fort drawn at any of four turns faces the same way relative to itself.
 */
final class FortDressing {

    private final GenContext ctx;
    private final Placement at;

    FortDressing(GenContext ctx, Placement at) {
        this.ctx = ctx;
        this.at = at;
    }

    /** World direction of the local vector, after the placement's turn. */
    int[] turn(int lx, int ly, int dx, int dy) {
        int[] a = at.cell(lx, ly);
        int[] b = at.cell(lx + dx, ly + dy);
        return new int[]{b[0] - a[0], b[1] - a[1]};
    }

    /** Whether the world cell is open enough to take a prop: in bounds, walkable, not a doorway, fixture or wall. */
    boolean open(int x, int y) {
        return ctx.grid.inBounds(x, y) && ctx.grid.isWalkable(x, y) && !ctx.grid.isDoorway(x, y)
                && !ctx.topology.isFixture(x, y) && !ctx.topology.isWall(x, y);
    }

    /** A sandbag piece in the local cell, bags toward the given local direction (and a second for a corner). */
    boolean sandbag(int lx, int ly, int dx, int dy, int cornerDx, int cornerDy) {
        int[] cell = at.cell(lx, ly);
        if (!open(cell[0], cell[1])) return false;
        String name;
        int[] a = turn(lx, ly, dx, dy);
        if (cornerDx != 0 || cornerDy != 0) {
            int[] b = turn(lx, ly, cornerDx, cornerDy);
            int[] ns = a[1] != 0 ? a : b;
            int[] ew = a[0] != 0 ? a : b;
            name = "corner-" + letter(ns) + letter(ew);
        } else {
            name = String.valueOf(letter(a));
        }
        DoodadDef def = TileRegistry.installed().doodad("doodad.fort-sandbag-" + name);
        if (def == null) return false;
        // Knee-high cover everyone steps over: the cell stays walkable and the doodad supplies the cover.
        ctx.grid.setSeeThrough(cell[0], cell[1], true);
        ctx.topology.setFixture(cell[0], cell[1], true);
        ctx.doodads.add(new Doodad(cell[0], cell[1], def));
        ctx.markMadeGround(cell[0], cell[1]);
        return true;
    }

    boolean sandbag(int lx, int ly, int dx, int dy) {
        return sandbag(lx, ly, dx, dy, 0, 0);
    }

    /** A run of straight sandbag from one local cell to another along one axis, bags toward (dx, dy). */
    void line(int x0, int y0, int x1, int y1, int dx, int dy) {
        int sx = Integer.signum(x1 - x0), sy = Integer.signum(y1 - y0);
        int x = x0, y = y0;
        while (true) {
            sandbag(x, y, dx, dy);
            if (x == x1 && y == y1) break;
            x += sx;
            y += sy;
        }
    }

    /**
     * A rectangular ring of sandbag at {@code distance} cells outside the box (x, y, w, h), bags facing outward, with
     * one gap of {@code gapWidth} cells centred on the box side given by (gapDx, gapDy) at offset {@code gapAt} along it.
     */
    void ring(int x, int y, int w, int h, int distance, int gapDx, int gapDy, int gapAt, int gapWidth) {
        int x0 = x - distance, y0 = y - distance, x1 = x + w - 1 + distance, y1 = y + h - 1 + distance;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                boolean edgeX = cx == x0 || cx == x1;
                boolean edgeY = cy == y0 || cy == y1;
                if (!edgeX && !edgeY) continue;
                if (inGap(cx, cy, x0, y0, x1, y1, gapDx, gapDy, gapAt, gapWidth)) continue;
                int ox = cx == x0 ? -1 : cx == x1 ? 1 : 0;
                int oy = cy == y0 ? -1 : cy == y1 ? 1 : 0;
                if (ox != 0 && oy != 0) sandbag(cx, cy, ox, 0, 0, oy);
                else sandbag(cx, cy, ox, oy);
            }
        }
    }

    /**
     * A berm two courses thick: the ring at {@code distance} and a second one just outside it, sharing the one gap, so a
     * single hairline of bags becomes a wall that reads at game scale and still turns the way in.
     */
    void thickRing(int x, int y, int w, int h, int distance, int gapDx, int gapDy, int gapAt, int gapWidth) {
        ring(x, y, w, h, distance, gapDx, gapDy, gapAt, gapWidth);
        ring(x, y, w, h, distance + 1, gapDx, gapDy, gapAt, gapWidth);
    }

    private static boolean inGap(int cx, int cy, int x0, int y0, int x1, int y1,
                                 int gapDx, int gapDy, int gapAt, int gapWidth) {
        int half = gapWidth / 2;
        if (gapDy != 0) {
            int edge = gapDy < 0 ? y0 : y1;
            return cy == edge && Math.abs(cx - gapAt) <= half;
        }
        if (gapDx != 0) {
            int edge = gapDx < 0 ? x0 : x1;
            return cx == edge && Math.abs(cy - gapAt) <= half;
        }
        return false;
    }

    /** One piece of wire (industrial fence), run horizontally or vertically in the local frame. Closed to movement. */
    boolean wire(int lx, int ly, boolean horizontal) {
        return barrier(lx, ly, horizontal, "doodad.industrial-fence-");
    }

    /** One run of concertina wire, the belt's own obstacle: closed to movement, breachable. */
    boolean concertina(int lx, int ly, boolean horizontal) {
        return barrier(lx, ly, horizontal, "doodad.fort-wire-");
    }

    private boolean barrier(int lx, int ly, boolean horizontal, String prefix) {
        int[] cell = at.cell(lx, ly);
        if (!open(cell[0], cell[1])) return false;
        int[] run = turn(lx, ly, horizontal ? 1 : 0, horizontal ? 0 : 1);
        String piece = run[0] != 0 ? "h" : "v";
        if (prefix.endsWith("fence-")) piece = "straight-" + piece;
        DoodadDef def = TileRegistry.installed().doodad(prefix + piece);
        if (def == null) return false;
        ctx.grid.setWalkable(cell[0], cell[1], false);
        ctx.grid.setSeeThrough(cell[0], cell[1], true);
        ctx.topology.setFixture(cell[0], cell[1], true);
        ctx.doodads.add(new Doodad(cell[0], cell[1], def));
        ctx.markMadeGround(cell[0], cell[1]);
        return true;
    }

    /** A trench section at the world cell, joined to whichever world neighbours are also trench. */
    void trench(int worldX, int worldY, Set<Long> trenchCells) {
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

    static long key(int x, int y) {
        return (long) x << 32 | (y & 0xffffffffL);
    }

    /** Bare earth: the trench floor, the firing steps and the gun pit's floor. */
    void earth(int lx, int ly) {
        int[] cell = at.cell(lx, ly);
        if (!ctx.grid.inBounds(cell[0], cell[1]) || !ctx.grid.isWalkable(cell[0], cell[1])
                || ctx.grid.isDoorway(cell[0], cell[1])) return;
        ctx.topology.setGroundKind(cell[0], cell[1], GroundKind.DIRT);
        ctx.topology.setSurface(cell[0], cell[1], ctx.topology.surfaceIndex("floors.dirt"));
        ctx.topology.setNatureOverlayIndex(cell[0], cell[1], -1);
        ctx.markMadeGround(cell[0], cell[1]);
    }

    /** Gravel bed under a gun or a plant. */
    void gravel(int lx, int ly) {
        int[] cell = at.cell(lx, ly);
        if (!ctx.grid.inBounds(cell[0], cell[1]) || !ctx.grid.isWalkable(cell[0], cell[1])
                || ctx.grid.isDoorway(cell[0], cell[1])) return;
        ctx.topology.setGroundKind(cell[0], cell[1], GroundKind.STONE);
        ctx.topology.setSurface(cell[0], cell[1], ctx.topology.surfaceIndex("floors.stone"));
        ctx.markMadeGround(cell[0], cell[1]);
    }

    /**
     * A catalogue prop with its anchor at the local cell, closed to movement across its whole footprint. Refused
     * unless every cell is open, so furniture never lands on a lane, a doorway or another prop.
     */
    boolean prop(int lx, int ly, String id, boolean[][] keepClear) {
        DoodadDef def = TileRegistry.installed().doodad("doodad." + id);
        if (def == null) return false;
        // The footprint extends from the anchor in the world's own axes, so find the world anchor of the local block.
        int[] a = at.cell(lx, ly);
        int[] b = at.cell(lx + def.footprintCellsX - 1, ly + def.footprintCellsY - 1);
        int minX = Math.min(a[0], b[0]), minY = Math.min(a[1], b[1]);
        int w = Math.abs(a[0] - b[0]) + 1, h = Math.abs(a[1] - b[1]) + 1;
        // A yard prop is laid at the world footprint of its local block, turned with the fort.
        Doodad prop = new Doodad(minX, minY, def, at.pose().quarterTurns());
        if (prop.footprintCellsX != w || prop.footprintCellsY != h) return false;
        for (int y = minY; y < minY + h; y++) {
            for (int x = minX; x < minX + w; x++) {
                if (!open(x, y)) return false;
                if (keepClear != null && keepClear[x][y]) return false;
            }
        }
        for (int y = minY; y < minY + h; y++) {
            for (int x = minX; x < minX + w; x++) {
                ctx.grid.setWalkable(x, y, false);
                ctx.grid.setSeeThrough(x, y, true);
                ctx.topology.setFixture(x, y, true);
                ctx.markMadeGround(x, y);
            }
        }
        ctx.doodads.add(prop);
        return true;
    }

    private static char letter(int[] dir) {
        if (dir[1] > 0) return 'n';
        if (dir[1] < 0) return 's';
        return dir[0] > 0 ? 'e' : 'w';
    }
}
