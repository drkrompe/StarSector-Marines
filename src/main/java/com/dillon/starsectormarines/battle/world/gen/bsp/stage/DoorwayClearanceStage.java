package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;

/**
 * Nothing stands in a doorway.
 *
 * <p>A door is cut by whichever pass built the wall it is in, and the ground
 * outside it is dressed by a pass that runs later and knows nothing about it.
 * Measured over ten generated cities, thirty of six and a half thousand
 * doorways opened straight into something solid — a crate, a bollard, a
 * compound's own fence. None of them sealed a building, because every one had
 * another way in; all of them read as broken, which is how they were noticed.
 *
 * <p>Two cases, and they want opposite answers.
 *
 * <p><b>Scenery in front of a door is scenery in the wrong place.</b> A crate
 * on a street cell does not stop being a crate because a door faces it, so the
 * crate goes and the cell is walkable again. Removing it can only add
 * connectivity, which is why it needs no further argument.
 *
 * <p><b>A door onto a facility's own surface never opened onto the street.</b>
 * A compound's perimeter is not clutter, and punching a hole in it to honour a
 * neighbour's door gives a civilian building a private entrance into a fenced
 * lot. The honest reading is that the wall should have been solid there, so the
 * threshold becomes wall — and only when it is a dead-end stub, which is a
 * local check that the seal cannot cut anything off. Measured, twenty-nine of
 * those thirty doorways already were one.
 *
 * <p>Deliberately a reconciliation rather than a rule imposed on the passes
 * that place things. Any of them may legitimately want that cell, none can see
 * what the others did, and the question is only answerable once they have all
 * finished.
 */
public final class DoorwayClearanceStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        for (int y = 1; y < ctx.height - 1; y++) {
            for (int x = 1; x < ctx.width - 1; x++) {
                if (!isThreshold(ctx, x, y)) continue;
                clearWayThrough(ctx, x, y);
            }
        }
    }

    /**
     * Whether this cell is a gap in a wall run — a door, in the only sense the
     * finished map records one.
     *
     * <p>Walls on both sides along exactly one axis. Both axes is a crossing
     * rather than a doorway, and neither is open ground.
     */
    private static boolean isThreshold(GenContext ctx, int x, int y) {
        CellTopology topology = ctx.topology;
        if (topology.isWall(x, y) || !ctx.grid.isWalkable(x, y)) return false;
        boolean acrossX = topology.isWall(x - 1, y) && topology.isWall(x + 1, y);
        boolean acrossY = topology.isWall(x, y - 1) && topology.isWall(x, y + 1);
        return acrossX != acrossY;
    }

    /** Deal with whichever way through this threshold is stopped by something placed. */
    private static void clearWayThrough(GenContext ctx, int x, int y) {
        boolean acrossX = ctx.topology.isWall(x - 1, y) && ctx.topology.isWall(x + 1, y);
        int[][] through = acrossX
                ? new int[][]{ { 0, 1 }, { 0, -1 } }
                : new int[][]{ { 1, 0 }, { -1, 0 } };
        for (int[] step : through) {
            int nx = x + step[0];
            int ny = y + step[1];
            if (ctx.grid.canTraverseCellStep(x, y, nx, ny)) continue;
            if (ctx.topology.isWall(nx, ny)) continue;      // a wall is not a blockage
            if (!blockedByAProp(ctx, nx, ny)) continue;
            if (ctx.isMadeGround(nx, ny)) sealThreshold(ctx, x, y);
            else clearCell(ctx, nx, ny);
            return;
        }
    }

    /** Whether a placed prop is what stops this cell being entered. */
    private static boolean blockedByAProp(GenContext ctx, int x, int y) {
        GroundKind ground = ctx.topology.getGroundKind(x, y);
        // Ground nobody could stand on either way. A door onto water is a
        // window with ambitions, and making it walkable would be worse than
        // leaving it shut.
        if (ground == GroundKind.WATER || ground == GroundKind.VOID) return false;
        return propAt(ctx, x, y) != null;
    }

    private static Doodad propAt(GenContext ctx, int x, int y) {
        for (Doodad doodad : ctx.doodads) {
            if (doodad.cover == 0) continue;
            if (x < doodad.cellX || x > doodad.cellX + doodad.footprintCellsX - 1) continue;
            if (y < doodad.cellY || y > doodad.cellY + doodad.footprintCellsY - 1) continue;
            return doodad;
        }
        return null;
    }

    /** Take the prop away and give the cell back. */
    private static void clearCell(GenContext ctx, int x, int y) {
        ctx.doodads.removeIf(doodad -> doodad.cover > 0
                && x >= doodad.cellX && x <= doodad.cellX + doodad.footprintCellsX - 1
                && y >= doodad.cellY && y <= doodad.cellY + doodad.footprintCellsY - 1);
        ctx.topology.setFixture(x, y, false);
        ctx.grid.setWalkable(x, y, true);
        ctx.grid.openAllEdges(x, y);
    }

    /**
     * Wall the threshold, taking the look of the run it sits in.
     *
     * <p>Refused unless the threshold is a dead end, which is the whole of the
     * safety argument: walling a cell with one way out removes a stub, and
     * walling one with two severs whatever they joined. Refused outright on a
     * reserved road cell, which is owed to a consumer outside the generator.
     */
    private static void sealThreshold(GenContext ctx, int x, int y) {
        boolean[][] roadReservation = ctx.get(BspKeys.ROAD_RESERVATION);
        if (roadReservation != null && roadReservation[x][y]) return;

        int ways = 0;
        for (int[] step : new int[][]{ { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            if (ctx.grid.canTraverseCellStep(x, y, x + step[0], y + step[1])) ways++;
        }
        if (ways > 1) return;

        ctx.grid.setWalkable(x, y, false);
        ctx.topology.setWall(x, y, true);
        ctx.topology.setFixture(x, y, false);
        // The run this cell closes is already drawn; borrowing a neighbour's
        // facing keeps the new piece part of the same wall rather than a
        // differently-lit block dropped into it.
        boolean acrossX = ctx.topology.isWall(x - 1, y) && ctx.topology.isWall(x + 1, y);
        int mask = acrossX
                ? ctx.topology.getWallDirMask(x - 1, y) | ctx.topology.getWallDirMask(x + 1, y)
                : ctx.topology.getWallDirMask(x, y - 1) | ctx.topology.getWallDirMask(x, y + 1);
        ctx.topology.orWallDirMask(x, y, mask);
    }
}
