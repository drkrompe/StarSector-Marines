package com.dillon.starsectormarines.battle.door;

import com.dillon.starsectormarines.battle.sally.SallyDoors;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.MapEditor;

/**
 * A door that stays shut until something unlocks it ({@code garrisonables.md}, Locked doors and hardened walls). Its
 * cells are shut at setup as the wall beside them; while its lock is open they stand open as passage, and once it
 * locks again they shut as soon as nobody stands in the door or the room it keeps. What unlocks it is the owner's
 * puzzle (a ruins site's power, a code, a trigger): the door reads only whether it is unlocked.
 *
 * <p>A <b>forcible</b> door is shut as an ordinary wall, so a breach through it forces it open for good. A
 * <b>hardened</b> door is shut as a {@link HardenedWalls hardened} wall that nothing breaks: its lock is the only way
 * through.
 */
public final class LockedDoor {

    /** How far from a door cell the wall it is shut as may stand: a span may meet its wall line a cell or two off. */
    public static final int JAMB_REACH = 3;

    private final String id;
    private final int[][] cells;
    private final int likeX, likeY;
    private final int[] keep;
    private final boolean hardened;
    private MapEditor.PassageRecord[] shut;
    private boolean spansX;
    private boolean ready;
    private boolean open;
    private boolean forced;

    /**
     * @param id       stable id, for messages
     * @param cells    the door's cells, {x, y} each
     * @param likeX    with {@code likeY}, an open cell whose ground the opened door takes
     * @param keep     {minX, minY, maxX, maxY}: while anybody stands in it the door does not shut
     * @param hardened whether it is shut as a wall nothing breaks
     */
    public LockedDoor(String id, int[][] cells, int likeX, int likeY, int[] keep, boolean hardened) {
        if (id == null || cells == null || cells.length == 0 || keep == null || keep.length != 4) {
            throw new IllegalArgumentException("a locked door needs an id, cells and a keep");
        }
        this.id = id;
        this.cells = cells;
        this.likeX = likeX;
        this.likeY = likeY;
        this.keep = keep;
        this.hardened = hardened;
    }

    public boolean isOpen() { return open; }
    /** Whether a breach forced it open for good; never for a hardened door. */
    public boolean isForced() { return forced; }
    public boolean hardened() { return hardened; }
    /** Whether its lock has what it can give (power, say) but still waits on something else: drawn with an amber lamp. */
    public boolean ready() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
    /** The door's cells, {x, y} each; read only. */
    public int[][] cells() { return cells; }
    /** The middle of the door's keep, in cells: the side of the door its room lies on. */
    public float keepCentreX() { return (keep[0] + keep[2] + 1) * 0.5f; }
    public float keepCentreY() { return (keep[1] + keep[3] + 1) * 0.5f; }
    /** Whether the door runs along x (a passage through it runs north-south); known once installed. */
    public boolean spansX() { return spansX; }

    /** Shuts the door, locked, as the nearest wall to each of its cells. At setup. */
    public void install(BattleSimulation sim) {
        if (shut != null) return;
        spansX = spansX(sim, cells);
        MapEditor editor = sim.mapEditor();
        shut = new MapEditor.PassageRecord[cells.length];
        for (int c = 0; c < cells.length; c++) {
            MapEditor.PassageRecord wall = jamb(sim, editor, cells[c][0], cells[c][1]);
            if (wall == null) throw new IllegalStateException(id + " has no wall beside it to shut as");
            shut[c] = wall;
        }
        close(sim);
        sim.addLockedDoor(this);
    }

    /** A span runs along its cells; a single open cell runs across the passage its floor continues. */
    static boolean spansX(BattleSimulation sim, int[][] cells) {
        if (cells.length > 1) return cells[0][1] == cells[cells.length - 1][1];
        int x = cells[0][0], y = cells[0][1];
        return sim.getGrid().isWalkable(x, y + 1) || sim.getGrid().isWalkable(x, y - 1);
    }

    /**
     * Brings the door to its lock: forced open for good by a breach of a forcible door, open while unlocked, and shut
     * again once locked and clear.
     */
    public void settle(BattleSimulation sim, boolean unlocked) {
        if (shut == null || forced) return;
        if (!open && !hardened && breached(sim, cells)) {
            forced = true;
            open = true;
            return;
        }
        if (unlocked && !open) {
            for (int[] c : cells) sim.mapEditor().openPassage(c[0], c[1], likeX, likeY);
            open = true;
        } else if (!unlocked && open && !occupied(sim)) {
            close(sim);
            dropRoutesThrough(sim);
        }
    }

    private void close(BattleSimulation sim) {
        for (MapEditor.PassageRecord wall : shut) {
            sim.mapEditor().closePassage(wall);
            sim.getGrid().markHardened(wall.x(), wall.y(), hardened);
        }
        open = false;
    }

    /** Whether any of the cells stands open. */
    public static boolean breached(BattleSimulation sim, int[][] cells) {
        for (int[] c : cells) if (sim.getGrid().isWalkable(c[0], c[1])) return true;
        return false;
    }

    /** The nearest wall to a door cell, recorded as if it stood in the door cell; null when none is in reach. */
    private MapEditor.PassageRecord jamb(BattleSimulation sim, MapEditor editor, int x, int y) {
        for (int r = 1; r <= JAMB_REACH; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.abs(dx) + Math.abs(dy) != r) continue;
                    int nx = x + dx, ny = y + dy;
                    if (!sim.getGrid().inBounds(nx, ny) || sim.getGrid().isWalkable(nx, ny) || inDoor(nx, ny)) continue;
                    MapEditor.PassageRecord w = editor.recordPassage(nx, ny);
                    return new MapEditor.PassageRecord(x, y, w.flags(), w.edges(), w.wallHp(), w.wall(), w.window(),
                            w.fixture(), w.groundKind(), w.surface(), w.ownCover(), w.ownCatch(), w.towardCover(),
                            w.towardCatch());
                }
            }
        }
        return null;
    }

    private boolean inDoor(int x, int y) {
        for (int[] c : cells) if (c[0] == x && c[1] == y) return true;
        return false;
    }

    /** Whether any body on the ground stands in the door or the room it keeps. */
    private boolean occupied(BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.isRiding(unit) || sim.identity().type(unit).isStatic()) continue;
            float x = sim.world().x(unit), y = sim.world().y(unit);
            if (x >= keep[0] && x < keep[2] + 1 && y >= keep[1] && y < keep[3] + 1) return true;
            for (int[] c : cells) {
                if (Math.abs(x - (c[0] + .5f)) < 1.5f && Math.abs(y - (c[1] + .5f)) < 1.5f) return true;
            }
        }
        return false;
    }

    private void dropRoutesThrough(BattleSimulation sim) {
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int[] c : cells) {
            box[0] = Math.min(box[0], c[0]);
            box[1] = Math.min(box[1], c[1]);
            box[2] = Math.max(box[2], c[0] + 1f);
            box[3] = Math.max(box[3], c[1] + 1f);
        }
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.isRiding(unit) || sim.identity().type(unit).isStatic()) continue;
            if (SallyDoors.routeCrosses(sim, unit, box, sim.physicalRadius(unit) + 0.5f)) sim.clearPath(unit);
        }
    }
}
