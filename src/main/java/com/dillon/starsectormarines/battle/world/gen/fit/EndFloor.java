package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.Arrays;
import java.util.List;

/**
 * A small room's floor in its canonical frame, optionally turned end for end
 * or side for side, for the compact fittings that keep their quiet half — a
 * pool, a chapel, the cells — away from the hatches.
 *
 * <p>Those rooms are placed with a door wherever the deck offers one, so the
 * fitting finds out where its hatches are only when it is furnished. Mirroring
 * the authored arrangement is how it puts the end nobody walks through at the
 * end nobody walks in at, without a second arrangement to keep in step.
 * Mirroring only moves footprints; a sprite keeps the room's own pose, so an
 * arrangement mirrored across its depth must choose its own facing pieces.
 */
final class EndFloor {
    final RoomFloor floor;
    final int width;
    final int depth;
    private final boolean flipAlong;
    private final boolean flipAcross;

    EndFloor(RoomFloor floor, boolean flipAlong, boolean flipAcross) {
        this.floor = floor;
        this.width = floor.canonicalWidth();
        this.depth = floor.canonicalHeight();
        this.flipAlong = flipAlong;
        this.flipAcross = flipAcross;
    }

    boolean flippedAcross() {
        return flipAcross;
    }

    /**
     * Hatches in the canonical frame, as {@code {x, y}} on the bulkhead ring.
     */
    static List<int[]> canonicalDoors(RoomFloor floor) {
        return floor.localDoors().stream().map(d -> floor.toCanonical(d.x(), d.y())).toList();
    }

    /** How many hatches open onto the far half of the room's length. */
    static int doorsAtFarEnd(RoomFloor floor) {
        int w = floor.canonicalWidth();
        return (int) canonicalDoors(floor).stream().filter(d -> d[0] >= w / 2).count();
    }

    private int x(int x, int span) {
        return flipAlong ? width - x - span : x;
    }

    private int y(int y, int span) {
        return flipAcross ? depth - y - span : y;
    }

    int[] local(int x, int y) {
        return floor.toLocal(x(x, 1), y(y, 1));
    }

    void reserve(int x, int y, int spanX, int spanY) {
        int[] r = floor.toLocalRect(x(x, spanX), y(y, spanY), spanX, spanY);
        floor.reserveLane(r[0], r[1], r[2], r[3]);
    }

    void closeOff(int x, int y, int spanX, int spanY) {
        int[] r = floor.toLocalRect(x(x, spanX), y(y, spanY), spanX, spanY);
        floor.closeOff(r[0], r[1], r[2], r[3]);
    }

    void ground(int x, int y, int spanX, int spanY, GroundKind kind) {
        int[] r = floor.toLocalRect(x(x, spanX), y(y, spanY), spanX, spanY);
        floor.markGround(r[0], r[1], r[2], r[3], kind);
    }

    void surface(int x, int y, int spanX, int spanY, String blockId) {
        int[] r = floor.toLocalRect(x(x, spanX), y(y, spanY), spanX, spanY);
        floor.markFloorSurface(r[0], r[1], r[2], r[3], blockId);
    }

    boolean free(int x, int y, int spanX, int spanY) {
        int[] r = floor.toLocalRect(x(x, spanX), y(y, spanY), spanX, spanY);
        return floor.isFree(r[0], r[1], r[2], r[3]);
    }

    /** A whole assembly or nothing; layers sit on the base's own cells. */
    boolean put(int x, int y, String baseId, String... layers) {
        DoodadDef base = TileRegistry.installed().doodad(baseId);
        if (base == null) return false;
        List<LayoutOp.Layer> stack = Arrays.stream(layers)
                .map(id -> new LayoutOp.Layer(0, 0, id)).toList();
        return floor.placeAssembly(x(x, base.footprintCellsX), y(y, base.footprintCellsY), baseId, stack);
    }

    /** A loose one-cell fixture, which claims its cell without blocking it. */
    boolean place(int x, int y, String doodadId) {
        int[] cell = local(x, y);
        return floor.place(doodadId, cell[0], cell[1]);
    }

    boolean partition(int x, int y, int spanX, int spanY, String blockId) {
        return floor.partition(x(x, spanX), y(y, spanY), spanX, spanY, blockId);
    }

    void pave(int x, int y, String doodadId) {
        int[] cell = local(x, y);
        floor.pave(cell[0], cell[1], doodadId);
    }

    boolean task(int standX, int standY, Affordance work, int focusX, int focusY) {
        int[] stand = local(standX, standY);
        int[] focus = local(focusX, focusY);
        return floor.fixtureTask(stand[0], stand[1], work, focus[0], focus[1]);
    }

    /** Keep a clear landing inside every hatch before anything is placed. */
    void clearHatches() {
        for (Doorway door : floor.localDoors()) floor.reserveLane(door.x() - 1, door.y() - 1, 3, 3);
    }

    /** Whether a canonical cell lies within a step of a hatch, where nobody should be posted. */
    boolean besideHatch(int x, int y) {
        int[] cell = local(x, y);
        for (Doorway door : floor.localDoors())
            if (Math.abs(door.x() - cell[0]) <= 2 && Math.abs(door.y() - cell[1]) <= 2) return true;
        return false;
    }
}
