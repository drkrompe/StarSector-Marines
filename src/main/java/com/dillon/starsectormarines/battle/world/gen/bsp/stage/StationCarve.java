package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.StationGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * Shared carve primitives for the station layouts — converting solid hull into
 * walkable rooms and the doors/gates between them. The "convert only currently
 * solid cells" rule (so a door materializes only in the wall gap, leaving room
 * interiors untouched) is the same idiom {@code CorridorStage} uses for the BSP
 * station; this lifts it for the concentric layout's room + door carving.
 */
final class StationCarve {

    private StationCarve() {}

    /**
     * Convert one cell to walkable {@link RoomPurpose#CORRIDOR} (door/gate),
     * but only if it is currently solid. Cells already inside a room are left
     * as-is, so the door shows up solely in the wall gap it crosses.
     */
    static void carveDoorCell(GenContext ctx, int x, int y) {
        NavigationGrid grid = ctx.grid;
        if (!grid.inBounds(x, y) || grid.isWalkable(x, y)) return;
        grid.setWalkableFloor(x, y);
        CellTopology topo = ctx.topology;
        topo.setGroundKind(x, y, GroundKind.STRIPED);
        topo.setRoomPurpose(x, y, RoomPurpose.CORRIDOR);
    }

    /**
     * Carve a door between two rooms and record the edge on the graph — but only
     * if the carve actually connected them ({@link #carveDoorBetween} returned
     * true), so the published {@link StationGraph} never claims a connection the
     * cells don't have. The shared connect primitive for the ring layouts.
     */
    static void connect(GenContext ctx, StationGraph graph, StationGraph.Room a, StationGraph.Room b) {
        if (carveDoorBetween(ctx, a, b)) {
            graph.addCorridor(a.id, b.id);
        }
    }

    /** Carve an inclusive rect as walkable {@link GroundKind#INDOOR} room floor. */
    static void carveRoomRect(GenContext ctx, int left, int top, int right, int bottom) {
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setGroundKind(x, y, GroundKind.INDOOR);
            }
        }
    }

    /**
     * Carve a 2-wide door through the wall gap between two axis-aligned-adjacent
     * rooms, centered on their shared-edge overlap. Only solid gap cells are
     * converted (room interiors are skipped), so the result is a clean opening
     * meeting both interiors.
     *
     * @return {@code true} if a connecting door was carved; {@code false} if the
     *     rooms aren't adjacent / their interiors don't overlap (nothing carved).
     *     Callers record a graph edge only on {@code true}, so the published
     *     {@link StationGraph} never claims a connection the cells don't have.
     */
    static boolean carveDoorBetween(GenContext ctx, StationGraph.Room a, StationGraph.Room b) {
        if (a.right < b.left || b.right < a.left) {
            // Horizontal adjacency — gap runs along x.
            StationGraph.Room left  = a.right < b.left ? a : b;
            StationGraph.Room right = left == a ? b : a;
            int y0 = Math.max(left.top, right.top) + 1;        // interior overlap (inset by 1)
            int y1 = Math.min(left.bottom, right.bottom) - 1;
            if (y1 < y0) return false;
            int yA = (y0 + y1) / 2;
            int yB = yA + 1 <= y1 ? yA + 1 : yA - 1;
            for (int x = left.right - 1; x <= right.left + 1; x++) {
                carveDoorCell(ctx, x, yA);
                if (yB >= y0) carveDoorCell(ctx, x, yB);
            }
            return true;
        } else if (a.bottom < b.top || b.bottom < a.top) {
            // Vertical adjacency — gap runs along y.
            StationGraph.Room top = a.bottom < b.top ? a : b;
            StationGraph.Room bot = top == a ? b : a;
            int x0 = Math.max(top.left, bot.left) + 1;
            int x1 = Math.min(top.right, bot.right) - 1;
            if (x1 < x0) return false;
            int xA = (x0 + x1) / 2;
            int xB = xA + 1 <= x1 ? xA + 1 : xA - 1;
            for (int y = top.bottom - 1; y <= bot.top + 1; y++) {
                carveDoorCell(ctx, xA, y);
                if (xB >= x0) carveDoorCell(ctx, xB, y);
            }
            return true;
        }
        return false;
    }
}
