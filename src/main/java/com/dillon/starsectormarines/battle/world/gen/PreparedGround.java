package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
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

    /** The key {@link #trench} reads its neighbours by. */
    public static long key(int x, int y) {
        return (long) x << 32 | (y & 0xffffffffL);
    }
}
