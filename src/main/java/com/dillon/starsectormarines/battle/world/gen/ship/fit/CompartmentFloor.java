package com.dillon.starsectormarines.battle.world.gen.ship.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomPose;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
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
    private final boolean[][] claimed;
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
        this.claimed = new boolean[width][height];
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

    /** How this compartment was turned and flipped out of its fitting's canonical frame. */
    public RoomPose pose() {
        return compartment.pose();
    }

    /** Extent along the canonical frame's x axis — the room's length as it was authored. */
    public int canonicalWidth() {
        return pose().upright() ? width : height;
    }

    /** Extent along the canonical frame's y axis — the room's depth as it was authored. */
    public int canonicalHeight() {
        return pose().upright() ? height : width;
    }

    /**
     * Carry a cell from the fitting's canonical frame into this compartment's
     * own coordinates.
     *
     * <p>This is what a fitting uses instead of working out which way round it
     * is. Authoring once, facing one way, and mapping on the way down is the
     * difference between a room that mirrors correctly and a room that mirrors
     * its footprint while leaving its contents where they were.
     */
    public int[] toLocal(int x, int y) {
        return pose().map(x, y, canonicalWidth(), canonicalHeight());
    }

    /** Carry a cell back out of this compartment into the fitting's canonical frame. */
    public int[] toCanonical(int x, int y) {
        return pose().unmap(x, y, canonicalWidth(), canonicalHeight());
    }

    /**
     * Carry a canonical rectangle into this compartment, as
     * {@code {x, y, spanX, spanY}}.
     *
     * <p>A turn moves which corner is the origin, so both corners are carried
     * across and the result normalised. Mapping only the origin and keeping the
     * spans lays every reservation in a quarter-turned room off the room.
     */
    public int[] toLocalRect(int x, int y, int spanX, int spanY) {
        int[] near = toLocal(x, y);
        int[] far = toLocal(x + spanX - 1, y + spanY - 1);
        return new int[]{
                Math.min(near[0], far[0]), Math.min(near[1], far[1]),
                Math.abs(near[0] - far[0]) + 1, Math.abs(near[1] - far[1]) + 1 };
    }

    /**
     * Whether the compartment covers this cell of the fitting's canonical frame.
     *
     * <p>For the arrangements that are decided by the room's own outline rather
     * than by its bounding box. A firing range is an L because the ready end is
     * deeper than the lanes, and where it stops being deep is where the firing
     * line goes — so the fitting reads that off the footprint instead of being
     * told a number that would then have to be kept in step with the recipe.
     */
    public boolean contains(int along, int across) {
        if (along < 0 || across < 0
                || along >= canonicalWidth() || across >= canonicalHeight()) {
            return false;
        }
        int[] cell = toLocal(along, across);
        return compartment.shape().contains(cell[0], cell[1]);
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
     * Mark a run of this compartment's deck as a different kind of ground.
     *
     * <p>Some arrangements are read from the floor rather than from what stands
     * on it: a gantry bay is a marked-out rectangle whether or not a machine is
     * in it, and without the marking a row of bays reads as scattered tools with
     * gaps. This is the same signal the hand-authored Mech Lab painted, and it
     * is real topology rather than a rendering trick, so consumers see it too.
     */
    public void markGround(int x, int y, int spanX, int spanY, GroundKind kind) {
        for (int dx = 0; dx < spanX; dx++) {
            for (int dy = 0; dy < spanY; dy++) {
                int lx = x + dx;
                int ly = y + dy;
                if (lx < 0 || ly < 0 || lx >= width || ly >= height) continue;
                if (!compartment.shape().contains(lx, ly)) continue;
                ctx.topology.setGroundKind(left + lx, top + ly, kind);
            }
        }
    }

    /**
     * Lay one tile of floor covering, without claiming the cell.
     *
     * <p>Paving is not furniture. A marked-out bay floor still has to take the
     * gantry frame standing on it and the machine standing in it, so this
     * deliberately leaves the cell free — unlike {@link #place}, which is for
     * things that occupy the deck.
     *
     * <p>Pave before furnishing. Doodads draw in the order they are recorded, so
     * covering laid after a fixture is covering laid over it.
     */
    public void pave(int x, int y, int tileColumn, int tileRow) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        if (!compartment.shape().contains(x, y)) return;
        ctx.doodads.add(new Doodad(left + x, top + y,
                new TileManifest.TileFrame(tileColumn, tileRow),
                TileManifest.SHEET, Doodad.COVER_NONE));
    }

    /**
     * Record a machine berth over a run of this compartment's deck.
     *
     * <p>Reserves the footprint as circulation rather than claiming it, because
     * a berth has to stay clear: what stands there is a unit the host spawns,
     * not a fixture the map owns. A berth that got furnished would be a bay a
     * machine cannot be put into.
     */
    public int berth(int x, int y, int spanX, int spanY, Gantry.Facing facing) {
        reserveLane(x, y, spanX, spanY);
        // Half-extents cover an odd number of cells, so an even span has to round
        // down: a berth that claimed one cell more than was reserved would put
        // the machine through the frame beside it.
        int halfX = (spanX - 1) / 2;
        int halfY = (spanY - 1) / 2;
        ctx.gantries.add(new Gantry(left + x + halfX, top + y + halfY,
                halfX, halfY, facing));
        return ctx.gantries.size() - 1;
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
     * Place one fixture and record that there is work to be done at it.
     *
     * <p>The standing cell is found beside the fixture and reserved, because a
     * task point nothing can stand on is a task point nobody can use. Where
     * nothing adjacent is standable the fixture still goes down — it is furniture
     * either way — and simply affords nothing.
     */
    public boolean place(String doodadId, int x, int y, Affordance affordance) {
        if (!place(doodadId, x, y)) return false;
        DoodadDef def = TileRegistry.installed().doodad(doodadId);
        int[] standing = standingCell(x, y, def.footprintCellsX, def.footprintCellsY);
        if (standing != null) fixtureTask(standing[0], standing[1], affordance, x, y);
        return true;
    }

    /**
     * Record work at a cell the fitting has chosen itself.
     *
     * <p>For the cases where the room's own geometry decides where somebody
     * stands and a search beside the fixture would get it wrong — a technician
     * works on a berthed machine from the mouth of the bay, not from inside the
     * frame run down its side.
     *
     * @return whether the point was taken; a refusal is a cell already furnished
     */
    public boolean fixtureTask(int cellX, int cellY, Affordance affordance,
                             int fixtureX, int fixtureY) {
        if (!standable(cellX, cellY) || claimed[cellX][cellY]) return false;
        claimed[cellX][cellY] = true;
        reserveLane(cellX, cellY, 1, 1);
        ctx.fixtureTasks.add(FixtureTask.at(left + cellX, top + cellY, affordance,
                left + fixtureX, top + fixtureY));
        return true;
    }

    /** Record work done on whatever the host parks in {@code berth}. */
    public boolean berthFixtureTask(int cellX, int cellY, int berth,
                                  int fixtureX, int fixtureY) {
        if (!standable(cellX, cellY) || claimed[cellX][cellY]) return false;
        claimed[cellX][cellY] = true;
        reserveLane(cellX, cellY, 1, 1);
        ctx.fixtureTasks.add(FixtureTask.servingBerth(left + cellX, top + cellY, berth,
                left + fixtureX, top + fixtureY));
        return true;
    }

    /** Whether somebody can stand here: inside the room and not furnished. Lanes count. */
    private boolean standable(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height
                && compartment.shape().contains(x, y) && free[x][y];
    }

    /**
     * A cell beside a footprint to work from, preferring one already reserved as
     * circulation. Standing in the lane is what a technician actually does, and
     * a lane cell can never be furnished out from under the point later.
     *
     * <p>Cells already spoken for are skipped. Preferring the lane without this
     * quietly hands one cell to every fixture around it, because the first point
     * reserves it as lane and thereby makes it the preferred answer for all its
     * neighbours — three benches sharing one spot, and a capacity of three where
     * only one person can stand.
     */
    private int[] standingCell(int x, int y, int spanX, int spanY) {
        int[] fallback = null;
        for (int dx = -1; dx <= spanX; dx++) {
            for (int dy = -1; dy <= spanY; dy++) {
                boolean beside = dx == -1 || dy == -1 || dx == spanX || dy == spanY;
                boolean diagonal = (dx == -1 || dx == spanX) && (dy == -1 || dy == spanY);
                if (!beside || diagonal) continue;
                int cx = x + dx;
                int cy = y + dy;
                if (!standable(cx, cy) || claimed[cx][cy]) continue;
                if (lane[cx][cy]) return new int[]{ cx, cy };
                if (fallback == null) fallback = new int[]{ cx, cy };
            }
        }
        return fallback;
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
