package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import java.util.List;

/**
 * The sliding door in one compartment's doorway: two leaves across its one or
 * two doorway cells that part for whoever comes through and close behind them.
 *
 * <p><b>The door answers the crew; nothing orders it.</b> Anyone standing in
 * line with the doorway and within {@link #CALL_CELLS} of the bulkhead calls it
 * open, and once nobody is there it closes again. In line rather than merely
 * near: a crewman on the far side of the wall beside a door is close to it but
 * is not coming through. It is presentation only: the doorway's passage and
 * sight are the grid's, and nobody waits on the leaves.
 *
 * <p>A <b>locked</b> door is a sealed doorway, one the ship's condition or a
 * reserved compartment keeps shut. It never opens; its leaves show the lock.
 * Repair lays a new deck rather than unlocking this one.
 *
 * <p>Coordinates are continuous cell coordinates. The leaves span the door
 * along {@code spanX ? x : y} from {@code spanMin} to {@code spanMax} and stand
 * across the bulkhead at {@code across}, the doorway cells' centre line.
 */
public final class RoomDoor {

    /** Seconds from shut to fully open, and back. */
    public static final float TRAVEL_SECONDS = 0.45f;
    /** How far either side of the bulkhead a body may stand and still call the door. */
    public static final float CALL_CELLS = 1.6f;
    /** How far past either jamb, along the bulkhead, a body still counts as in line with the door. */
    static final float CALL_SLACK_CELLS = 0.35f;

    private final boolean spanX;
    private final float spanMin, spanMax, across;
    private final boolean locked;
    private float openness;

    public RoomDoor(boolean spanX, float spanMin, float spanMax, float across, boolean locked) {
        if (!(spanMax > spanMin)) throw new IllegalArgumentException("a door has width");
        this.spanX = spanX;
        this.spanMin = spanMin;
        this.spanMax = spanMax;
        this.across = across;
        this.locked = locked;
    }

    /**
     * The door across a compartment's doorway cells, which lie side by side
     * along the bulkhead. {@code locked} when the doorway is sealed.
     */
    public static RoomDoor across(DeckGraph.Compartment room, List<Doorway> cells, boolean locked) {
        if (cells.isEmpty()) throw new IllegalArgumentException("a door needs its doorway");
        Doorway first = cells.get(0);
        // The room lies across the door from the corridor: along y means the leaves span x.
        boolean spanX = room.contains(first.x(), first.y() - 1) || room.contains(first.x(), first.y() + 1);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (Doorway cell : cells) {
            int along = spanX ? cell.x() : cell.y();
            min = Math.min(min, along);
            max = Math.max(max, along);
        }
        float across = (spanX ? first.y() : first.x()) + 0.5f;
        return new RoomDoor(spanX, min, max + 1f, across, locked);
    }

    public boolean spanX() { return spanX; }
    public float spanMin() { return spanMin; }
    public float spanMax() { return spanMax; }
    public float across() { return across; }
    public boolean locked() { return locked; }
    public float centerX() { return spanX ? (spanMin + spanMax) * 0.5f : across; }
    public float centerY() { return spanX ? across : (spanMin + spanMax) * 0.5f; }
    /** A radius around the centre that holds every point that can call the door. */
    public float callRadius() { return (spanMax - spanMin) * 0.5f + CALL_SLACK_CELLS + CALL_CELLS; }

    /** Whether a body at {@code (x, y)} is in line with the doorway and near enough to call it. */
    public boolean calledBy(float x, float y) {
        float along = spanX ? x : y;
        float off = Math.abs((spanX ? y : x) - across);
        return along >= spanMin - CALL_SLACK_CELLS && along <= spanMax + CALL_SLACK_CELLS && off <= CALL_CELLS;
    }

    /** 0 shut, 1 fully open, linear in time. */
    public float openness() { return openness; }

    /** How far each leaf has withdrawn into its jamb, eased so the leaves start and stop gently. */
    public float leafTravel() {
        float t = openness;
        return t * t * (3f - 2f * t);
    }

    /** Moves the leaves toward open while called and toward shut otherwise; a locked door stays shut. */
    public void advance(float seconds, boolean called) {
        if (!(seconds > 0f)) return;
        float step = seconds / TRAVEL_SECONDS;
        openness = called && !locked ? Math.min(1f, openness + step) : Math.max(0f, openness - step);
    }
}
