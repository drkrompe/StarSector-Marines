package com.dillon.starsectormarines.battle.world.gen.ship.fit;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * One compartment's floor while it is being fitted out: what is free, what is
 * lane, and what has been furnished.
 *
 * <p>Themes work through this rather than touching cells directly, so the two
 * rules that make a fill legible hold everywhere instead of being re-argued per
 * room. <b>Lanes are reserved before fixtures</b> and nothing may be placed on
 * them, which is what keeps a furnished room walkable from its door. And a
 * fixture is only ever placed as part of a group, so a bunk arrives with its
 * locker rather than floating alone in the middle of the deck.
 *
 * <p>Nothing here decides where a room's furniture goes. That is the theme's
 * job; this only holds the floor and refuses the placements that would ruin it.
 */
public final class CompartmentFloor {

    private final GenContext ctx;
    private final DeckGraph.Compartment compartment;
    private final RoomFit fit;
    private final boolean[][] free;
    private final boolean[][] lane;
    private final int left;
    private final int top;
    private final int width;
    private final int height;
    private int placed;

    public CompartmentFloor(GenContext ctx, DeckGraph.Compartment compartment, RoomFit fit) {
        this.ctx = ctx;
        this.compartment = compartment;
        this.fit = fit;
        this.left = compartment.left();
        this.top = compartment.top();
        this.width = compartment.shape().width();
        this.height = compartment.shape().height();
        this.free = new boolean[width][height];
        this.lane = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                free[x][y] = compartment.shape().contains(x, y);
            }
        }
    }

    public RoomFit fit() {
        return fit;
    }

    public DeckGraph.Compartment compartment() {
        return compartment;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** How many fixtures this fitting has placed, which is the compartment's capacity. */
    public int placedFixtures() {
        return placed;
    }

    /** Whether the floor cell at local coordinates can still take a fixture. */
    public boolean isFree(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && free[x][y] && !lane[x][y];
    }

    /** Whether a whole footprint is free, so a group is placed entire or not at all. */
    public boolean isFree(int x, int y, int spanX, int spanY) {
        for (int dx = 0; dx < spanX; dx++) {
            for (int dy = 0; dy < spanY; dy++) {
                if (!isFree(x + dx, y + dy)) return false;
            }
        }
        return true;
    }

    /**
     * Reserve a run of cells as circulation. Lanes are authored first and are
     * never furnished, which is the whole of why a fitted room stays walkable.
     */
    public void reserveLane(int x, int y, int spanX, int spanY) {
        for (int dx = 0; dx < spanX; dx++) {
            for (int dy = 0; dy < spanY; dy++) {
                int lx = x + dx;
                int ly = y + dy;
                if (lx < 0 || ly < 0 || lx >= width || ly >= height) continue;
                if (free[lx][ly]) lane[lx][ly] = true;
            }
        }
    }

    /** Every door into this compartment, in the compartment's own coordinates. */
    public List<DeckGraph.Compartment.Door> localDoors() {
        List<DeckGraph.Compartment.Door> doors = new ArrayList<>();
        for (DeckGraph.Compartment.Door door : compartment.doors()) {
            doors.add(new DeckGraph.Compartment.Door(door.x() - left, door.y() - top));
        }
        return doors;
    }

    /**
     * Place one fixture, occupying its authored footprint.
     *
     * @return whether it went down; a refused placement is a theme asking for
     *     somewhere already taken, which is expected at the edges of a shape
     */
    public boolean place(String doodadId, int x, int y) {
        DoodadDef def = TileRegistry.installed().doodad(doodadId);
        if (def == null) return false;
        if (!isFree(x, y, def.footprintCellsX, def.footprintCellsY)) return false;
        for (int dx = 0; dx < def.footprintCellsX; dx++) {
            for (int dy = 0; dy < def.footprintCellsY; dy++) {
                free[x + dx][y + dy] = false;
            }
        }
        ctx.doodads.add(new Doodad(left + x, top + y, def));
        placed++;
        return true;
    }

    /**
     * Whether the doors still reach every reserved lane.
     *
     * <p>Run after a theme finishes. A fill that seals a room is worse than no
     * fill: the compartment still counts toward the deck, still shows a door,
     * and cannot be entered.
     *
     * <p>Deliberately checks the lanes rather than every open cell. Furniture
     * always strands the odd sliver behind itself — the corner past the end of a
     * bunk row, the gap between a rack and the bulkhead — and treating those as
     * failures rolled most of the deck back to bare floor for defects nobody
     * could walk into anyway. What has to hold is that the authored circulation
     * is intact.
     */
    public boolean circulationSurvives() {
        int[] start = null;
        for (DeckGraph.Compartment.Door door : localDoors()) {
            for (int[] step : STEPS) {
                int x = door.x() + step[0];
                int y = door.y() + step[1];
                if (x >= 0 && y >= 0 && x < width && y < height && free[x][y]) {
                    start = new int[]{ x, y };
                    break;
                }
            }
            if (start != null) break;
        }
        if (start == null) return compartment.doors().isEmpty();

        boolean[][] seen = new boolean[width][height];
        Deque<int[]> queue = new ArrayDeque<>();
        seen[start[0]][start[1]] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] step : STEPS) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (seen[nx][ny] || !free[nx][ny]) continue;
                seen[nx][ny] = true;
                queue.add(new int[]{ nx, ny });
            }
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (lane[x][y] && !seen[x][y]) return false;
            }
        }
        return true;
    }

    private static final int[][] STEPS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
}
