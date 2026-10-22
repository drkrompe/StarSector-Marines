package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.SwingDoor;
import com.dillon.starsectormarines.render2d.BattleCamera;
import java.util.Arrays;

/**
 * The hinged door in every one-cell doorway on a battle map, swung by whoever
 * is walking through it.
 *
 * <p>Drawn over the floor and under the people: the leaf hangs from one jamb,
 * shut across the opening, and swings into the building as someone comes in
 * line with the doorway ({@link SwingDoor}). A doorway blown to rubble or
 * walled over has no door. Ship decks draw their own sliding doors and are
 * skipped here; a wide doorway is a gap, not a door.
 *
 * <p>The doorway cells are found once per map. Map generation is the only
 * thing that tags a doorway, so a battle never adds one; what a battle does
 * change, a jamb blown down, is checked as each door is drawn.
 */
final class SwingDoorRenderSystem implements RenderSystem {

    private static final float LEAF_WIDTH = 0.13f;
    private static final float[] LEAF = {0.42f, 0.34f, 0.26f};
    private static final float[] EDGE = {0.16f, 0.13f, 0.11f};
    private static final float[] HINGE = {0.66f, 0.66f, 0.62f};

    private NavigationGrid indexedGrid;
    /** Packed {@code y * width + x} of every one-cell doorway, sorted. */
    private int[] doors = new int[0];
    private final LongBucket near = new LongBucket();

    @Override
    public RenderLayer layer() {
        return RenderLayer.DOODADS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (ctx.hostProfile == BattleRenderHostProfile.DECK_SCENE) return;
        BattleSimulation sim = ctx.sim;
        NavigationGrid grid = sim.getGrid();
        if (grid != indexedGrid) index(grid);
        if (doors.length == 0) return;
        CellTopology topology = sim.getTopology();
        UnitSpatialIndex units = sim.getUnitIndex();
        BattleCamera cam = ctx.camera;
        int width = grid.getWidth();
        int minX = (int) Math.floor(cam.screenToCellX(cam.vpX())) - 1;
        int maxX = (int) Math.ceil(cam.screenToCellX(cam.vpX() + cam.vpW())) + 1;
        int minY = (int) Math.floor(cam.screenToCellY(cam.vpY())) - 1;
        int maxY = (int) Math.ceil(cam.screenToCellY(cam.vpY() + cam.vpH())) + 1;
        int from = Arrays.binarySearch(doors, Math.max(0, minY) * width);
        if (from < 0) from = -from - 1;
        for (int i = from; i < doors.length; i++) {
            int x = doors[i] % width, y = doors[i] / width;
            if (y > maxY) break;
            if (x < minX || x > maxX) continue;
            if (topology.isWall(x, y) || topology.isRubble(x, y) || !grid.isWalkable(x, y)) continue;
            draw(ctx, out, topology, units, x, y);
        }
    }

    private void index(NavigationGrid grid) {
        indexedGrid = grid;
        int width = grid.getWidth(), height = grid.getHeight();
        int[] found = new int[16];
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!grid.isDoorway(x, y) || OpeningTiles.wideDoorway(grid, x, y)) continue;
                if (count == found.length) found = Arrays.copyOf(found, count * 2);
                found[count++] = y * width + x;
            }
        }
        doors = Arrays.copyOf(found, count);
    }

    private void draw(RenderContext ctx, DrawList out, CellTopology topology, UnitSpatialIndex units, int x, int y) {
        Direction facing = OpeningTiles.doorFacing(topology, x, y);
        boolean spanX = facing == Direction.N || facing == Direction.S;
        float open = 0f;
        if (units != null) {
            units.gather(x + 0.5f, y + 0.5f, SwingDoor.REACH_CELLS + 1f, near);
            var world = ctx.sim.world();
            for (int i = 0; i < near.size && open < 1f; i++) {
                long id = near.ids[i];
                if (world.hasPosition(id)) open = Math.max(open, SwingDoor.pushedBy(x, y, spanX, world.x(id), world.y(id)));
            }
        }
        // Into the building: the side of the doorway that is part of one, else the high side.
        int sx = spanX ? 0 : 1, sy = spanX ? 1 : 0;
        boolean lowIndoors = indoors(topology, x - sx, y - sy) && !indoors(topology, x + sx, y + sy);
        float into = lowIndoors ? -1f : 1f;
        boolean hingeLow = SwingDoor.hingeLow(x, y);
        float hingeAlong = hingeLow ? 0f : 1f;
        float toward = hingeLow ? 1f : -1f;
        double angle = open * Math.PI * 0.5;
        float reachAlong = (float) Math.cos(angle) * toward * 0.92f;
        float reachInto = (float) Math.sin(angle) * into * 0.92f;
        float hx = spanX ? x + hingeAlong : x + 0.5f;
        float hy = spanX ? y + 0.5f : y + hingeAlong;
        float tx = hx + (spanX ? reachAlong : reachInto);
        float ty = hy + (spanX ? reachInto : reachAlong);
        BattleCamera cam = ctx.camera;
        float px = cam.cellPxSize();
        float a = ctx.alphaMult;
        float sx0 = cam.cellToScreenX(hx), sy0 = cam.cellToScreenY(hy);
        float sx1 = cam.cellToScreenX(tx), sy1 = cam.cellToScreenY(ty);
        out.addLine(RenderLayer.DOODADS, sx0, sy0, sx1, sy1, Math.max(2f, px * (LEAF_WIDTH + 0.06f)),
                EDGE[0], EDGE[1], EDGE[2], a);
        out.addLine(RenderLayer.DOODADS, sx0, sy0, sx1, sy1, Math.max(1f, px * LEAF_WIDTH),
                LEAF[0], LEAF[1], LEAF[2], a);
        float pin = Math.max(1.5f, px * 0.09f);
        out.addSolidRect(RenderLayer.DOODADS, sx0 - pin, sy0 - pin, sx0 + pin, sy0 + pin,
                HINGE[0], HINGE[1], HINGE[2], a);
    }

    /** Inside a building; ground kind cannot say, since unset ground reads as indoor floor. */
    private static boolean indoors(CellTopology topology, int x, int y) {
        return topology.inBounds(x, y) && topology.getBuildingId(x, y) != 0;
    }
}
