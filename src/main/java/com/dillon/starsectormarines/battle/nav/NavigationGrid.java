package com.dillon.starsectormarines.battle.nav;

import java.util.Arrays;

/**
 * 2D navigation grid with per-cell walkability and per-edge passability.
 *
 * <p>Ported (slim) from MoonLight Engine's {@code engine.navigation.NavigationGrid}.
 * Dropped from the original: stair/gate/tower-entrance cell flags, cell heights,
 * the clearance map (for >1-cell-radius agents), sparse {@code CellMetadata},
 * the on-demand walkable-index, and the fastutil dependencies. Kept the
 * pathfinder hot-path accessors that take pre-computed flat indices and
 * pre-shifted edge masks.
 *
 * <p>Cell flags byte layout:
 * <ul>
 *   <li>Bit 0: walkable (agent can stand here)</li>
 *   <li>Bit 1: floor (has a floor surface — currently informational only)</li>
 * </ul>
 *
 * <p>Edge passability byte layout:
 * <ul>
 *   <li>Bits 0-3: cardinal (N, E, S, W)</li>
 *   <li>Bits 4-7: diagonal (NE, SE, SW, NW)</li>
 * </ul>
 *
 * <p>Default state: all zeros (not walkable, no edges passable). Map builders set
 * bits to 1. {@link #setWalkableFloor(int, int)} is the common-case convenience
 * that flags the cell walkable + floor and opens all eight edges.
 */
public class NavigationGrid {

    private static final int WALKABLE_BIT = 0;
    private static final int FLOOR_BIT    = 1;

    private final int width;
    private final int height;
    private final byte[] cellFlags;
    private final byte[] edgePassability;

    public NavigationGrid(int width, int height) {
        this.width = width;
        this.height = height;
        int size = width * height;
        this.cellFlags = new byte[size];
        this.edgePassability = new byte[size];
    }

    public int getWidth()  { return width;  }
    public int getHeight() { return height; }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /** Flat array index. Public for hot-path callers that bypass bounds checks. */
    public int index(int x, int y) {
        return y * width + x;
    }

    // ----- Hot-path accessors (no bounds check — caller guarantees validity) -----

    public boolean isWalkableAt(int idx) {
        return (cellFlags[idx] & (1 << WALKABLE_BIT)) != 0;
    }

    public boolean isEdgePassableAt(int idx, int mask) {
        return (edgePassability[idx] & mask) != 0;
    }

    // ----- Cell flags (bounds-checked) -----

    public boolean isWalkable(int x, int y) {
        if (!inBounds(x, y)) return false;
        return (cellFlags[index(x, y)] & (1 << WALKABLE_BIT)) != 0;
    }

    public void setWalkable(int x, int y, boolean walkable) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        if (walkable) cellFlags[idx] |= (byte) (1 << WALKABLE_BIT);
        else          cellFlags[idx] &= (byte) ~(1 << WALKABLE_BIT);
    }

    public boolean hasFloor(int x, int y) {
        if (!inBounds(x, y)) return false;
        return (cellFlags[index(x, y)] & (1 << FLOOR_BIT)) != 0;
    }

    public void setFloor(int x, int y, boolean floor) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        if (floor) cellFlags[idx] |= (byte) (1 << FLOOR_BIT);
        else       cellFlags[idx] &= (byte) ~(1 << FLOOR_BIT);
    }

    /** Marks the cell walkable + floor and opens all eight edges. */
    public void setWalkableFloor(int x, int y) {
        setWalkable(x, y, true);
        setFloor(x, y, true);
        openAllEdges(x, y);
    }

    // ----- Edge passability -----

    public boolean isEdgePassable(int x, int y, Direction dir) {
        if (!inBounds(x, y)) return false;
        return (edgePassability[index(x, y)] & (1 << dir.bit())) != 0;
    }

    public void setEdgePassable(int x, int y, Direction dir, boolean passable) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        if (passable) edgePassability[idx] |= (byte) (1 << dir.bit());
        else          edgePassability[idx] &= (byte) ~(1 << dir.bit());
    }

    public void openAllEdges(int x, int y) {
        if (!inBounds(x, y)) return;
        edgePassability[index(x, y)] = (byte) 0xFF;
    }

    /**
     * Blocks an edge on this cell only. The neighbor's reciprocal edge is NOT
     * modified — under the cell-local edge model the pathfinder checks both
     * sides at query time.
     */
    public void blockEdge(int x, int y, Direction dir) {
        setEdgePassable(x, y, dir, false);
    }

    public void openEdge(int x, int y, Direction dir) {
        setEdgePassable(x, y, dir, true);
    }

    // ----- Raw arrays (for the pathfinder's hot path) -----

    public byte[] getCellFlagsArray()        { return cellFlags;       }
    public byte[] getEdgePassabilityArray()  { return edgePassability; }

    public void clear() {
        Arrays.fill(cellFlags, (byte) 0);
        Arrays.fill(edgePassability, (byte) 0);
    }

    // ----- Line of sight -----

    /**
     * Bresenham trace from {@code (x0,y0)} to {@code (x1,y1)} — returns false
     * if any non-endpoint cell on the line is non-walkable. Endpoints are
     * exempt so a shooter can stand in a cell flagged non-walkable (they
     * normally don't) and a target's cell never blocks the shot at itself.
     *
     * <p>Basic Bresenham — doesn't visit every cell the geometric line
     * crosses, so a diagonal "tunnel" through corner-touching walls reads as
     * visible. Acceptable for an auto-battler; tighten with supercover
     * Bresenham if it becomes visually weird.
     */
    public boolean hasLineOfSight(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            boolean endpoint = (x == x0 && y == y0) || (x == x1 && y == y1);
            if (!endpoint && !isWalkable(x, y)) return false;
            if (x == x1 && y == y1) return true;
            int e2 = err << 1;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 <  dx) { err += dx; y += sy; }
        }
    }
}
