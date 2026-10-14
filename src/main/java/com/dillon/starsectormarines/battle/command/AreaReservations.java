package com.dillon.starsectormarines.battle.command;

import java.util.HashMap;
import java.util.Map;

/**
 * The defender commander's single per-area reservation table: which squads are already committed to answering which
 * pressure area, however they got there. A squad rallied to an area's staging cell by the track response, a patrol
 * waiting there for manning, and a squad on or ordered to a garrisonable inside it are all the same thing to the
 * area's demand: one squad, counted once. Where each used to be counted by a different mechanism the rally and the
 * flow competed for the same patrols and each answered an area the other had already answered.
 *
 * <p>A squad holds at most one reservation. Committing it again moves it (a rallied squad that is then sent to man a
 * place becomes manned, not both), so the totals cannot exceed the squads that exist. Pure bookkeeping over a handful of
 * areas; nothing here searches, and the table is rebuilt every command plan from the frozen frame.
 */
final class AreaReservations {

    /** How a squad answers its area. Declaration order is the order in which a squad is the area's to draw first. */
    enum Kind { MANNED, STAGED, RALLIED }

    private record Hold(int area, Kind kind) { }

    private final int areas;
    private final Map<Integer, Hold> holds = new HashMap<>();
    private final int[][] counts;

    AreaReservations(int areas) {
        this.areas = Math.max(0, areas);
        this.counts = new int[Kind.values().length][this.areas];
    }

    /** Reserves (or moves) a squad to an area. A negative or unknown area releases whatever it held. */
    void commit(int squadId, int area, Kind kind) {
        release(squadId);
        if (area < 0 || area >= areas) return;
        holds.put(squadId, new Hold(area, kind));
        counts[kind.ordinal()][area]++;
    }

    /** Drops a squad's reservation, if any. */
    void release(int squadId) {
        Hold old = holds.remove(squadId);
        if (old != null) counts[old.kind().ordinal()][old.area()]--;
    }

    /** The area this squad is reserved to, or -1. */
    int areaOf(int squadId) {
        Hold hold = holds.get(squadId);
        return hold == null ? -1 : hold.area();
    }

    /** How the squad is reserved, or {@code null}. */
    Kind kindOf(int squadId) {
        Hold hold = holds.get(squadId);
        return hold == null ? null : hold.kind();
    }

    int count(int area, Kind kind) {
        return area < 0 || area >= areas ? 0 : counts[kind.ordinal()][area];
    }

    /** Squads reserved to the area, whatever the kind. */
    int reserved(int area) {
        int total = 0;
        for (Kind kind : Kind.values()) total += count(area, kind);
        return total;
    }

    /** All squads reserved to any area. */
    int totalReserved() {
        return holds.size();
    }

    /** What an area still wants after its reservations and the free squads already near enough to count as local. */
    int unmet(int area, int demand, int nearby) {
        return Math.max(0, demand - reserved(area) - Math.max(0, nearby));
    }

    int areas() {
        return areas;
    }
}
