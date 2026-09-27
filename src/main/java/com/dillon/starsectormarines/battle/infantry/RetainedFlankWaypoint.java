package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Objects;

/**
 * One squad's accepted plan-time flank choice, owned by serial squad planning.
 * Periodic replanning may reuse the destination without repeating candidate
 * proofs. This is not a route proof from a moved origin: execution still owns
 * movement validation and structural-failure handback.
 *
 * <p>The caller must clear this choice when the goal exits, the plan completes,
 * or its executable assignment changes, and must run ordinary goal relevance
 * before lookup. No decision here grants permission to continue an old goal.
 * Worker-side attack-move flank queries do not use this state.
 */
public final class RetainedFlankWaypoint {
    static final int BASE_TTL = 300;
    static final int MAX_DISPLACEMENT_CELLS = 4;
    private final int lifetime;
    private Entry entry;

    public record Cell(int x, int y) {}

    private record Entry(NavigationGrid grid, long topology,
                         ObjectiveAssignment assignment, long contactId,
                         int contactX, int contactY, int originX, int originY,
                         int rawX, int rawY, boolean cardinal,
                         int pickedTick, Cell cell) {}

    public RetainedFlankWaypoint(int squadId) {
        lifetime = BASE_TTL + Math.floorMod(squadId, 61);
    }

    /** Returns the retained cell or retires it on any invalidation. Hits never renew its anchors or age. */
    public Cell lookup(NavigationGrid grid, ObjectiveAssignment assignment, long contactId,
                       int contactX, int contactY, int originX, int originY,
                       int rawX, int rawY, boolean cardinal, int tick) {
        Entry saved = entry;
        if (saved == null) return null;
        long age = (long) tick - saved.pickedTick;
        if (saved.grid != grid || saved.topology != grid.topologyRevision()
                || !Objects.equals(saved.assignment, assignment)
                || saved.contactId != contactId || saved.cardinal != cardinal
                || age < 0 || age >= lifetime
                || displaced(saved.contactX, saved.contactY, contactX, contactY)
                || displaced(saved.originX, saved.originY, originX, originY)
                || displaced(saved.rawX, saved.rawY, rawX, rawY)
                || !usable(grid, saved.cell.x, saved.cell.y)) {
            clear();
            return null;
        }
        return saved.cell;
    }

    /**
     * Stores only an accepted, non-origin destination after the caller's route
     * and detour checks. The origin is the selector's refusal sentinel and
     * never acquires a retained lifetime, even if it happens to be walkable.
     */
    public void remember(NavigationGrid grid, ObjectiveAssignment assignment, long contactId,
                         int contactX, int contactY, int originX, int originY,
                         int rawX, int rawY, boolean cardinal, int tick,
                         int waypointX, int waypointY) {
        clear();
        if (waypointX == originX && waypointY == originY
                || !usable(grid, waypointX, waypointY)) return;
        entry = new Entry(grid, grid.topologyRevision(), assignment, contactId,
                contactX, contactY, originX, originY, rawX, rawY, cardinal,
                tick, new Cell(waypointX, waypointY));
    }

    public void clear() {
        entry = null;
    }

    private static boolean displaced(int anchorX, int anchorY, int x, int y) {
        double dx = (double) x - anchorX;
        double dy = (double) y - anchorY;
        return dx * dx + dy * dy >= MAX_DISPLACEMENT_CELLS * MAX_DISPLACEMENT_CELLS;
    }

    private static boolean usable(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y);
    }
}
