package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveOrder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Which of the player's squads stand in the <b>GARRISON</b> stance, and what each is doing about it: the data side of
 * a player's opt-in reactive manning (`garrisonables.md`, `ai-nouns.md`). The stance is a standing permission, not an
 * order: a squad in it that carries no player order watches the ground around its anchor and mans a nearby place its
 * side believes pressed, through the same occupy and hold orders a click gives. {@link ReactiveManningSystem} acts on
 * it; the UI toggles it and reads {@link #view}.
 *
 * <p>Toggles may arrive from the UI between ticks; every method is synchronized, and the system reads a copy.
 */
public final class ReactiveManningService {

    /** What a squad's stance is doing, for the order bar and the map. */
    public enum Status {
        /** Not in the stance. */
        OFF,
        /** In the stance, carrying no order: watching the ground round its anchor. */
        WATCHING,
        /** Manning a fighting position the stance sent it to. */
        MANNING_TRENCH,
        /** Holding a building the stance sent it to. */
        HOLDING_BUILDING,
        /** Walking back to its anchor after the pressure passed or it fell back. */
        RETURNING,
        /** Carrying a player order, which the stance leaves alone until it ends. */
        UNDER_ORDERS
    }

    /**
     * A squad's stance as the UI sees it. The anchor is meaningful from {@link Status#WATCHING} on; the place is the
     * cell of the place being manned (a run's centre, a building's anchor) and {@code -1} otherwise.
     */
    public record View(Status status, int anchorX, int anchorY, int placeX, int placeY) {
        static final View OFF = new View(Status.OFF, -1, -1, -1, -1);
    }

    /** One squad's stance. Mutated only by the system, on the simulation thread. */
    static final class Watch {
        static final int UNSET = Integer.MIN_VALUE;

        Status status = Status.WATCHING;
        int anchorX = UNSET;
        int anchorY = UNSET;
        /** The order the stance itself gave and the squad still carries, or null. */
        ActiveOrder issued;
        /** The place being manned: a run's centre cell or a building's anchor, and the building itself. */
        int placeX = -1;
        int placeY = -1;
        TacticalNode node;
        int coolPulses;
        /** Places this squad was breached out of, by key, and the tick each may be manned again. */
        final Map<Long, Integer> lockedUntil = new HashMap<>();
        boolean switchedOff;

        boolean anchored() { return anchorX != UNSET; }

        void clearPlace() {
            placeX = -1;
            placeY = -1;
            node = null;
            coolPulses = 0;
        }

        View view() {
            boolean placed = status == Status.MANNING_TRENCH || status == Status.HOLDING_BUILDING;
            return new View(status, anchored() ? anchorX : -1, anchored() ? anchorY : -1,
                    placed ? placeX : -1, placed ? placeY : -1);
        }
    }

    private final Map<Integer, Watch> watches = new TreeMap<>();
    private final Map<Integer, View> views = new HashMap<>();

    /** Puts the squad in the stance or takes it out. Taking it out releases the order the stance gave, if any. */
    public synchronized void setEnabled(int squadId, boolean on) {
        Watch watch = watches.get(squadId);
        if (on) {
            if (watch == null) {
                watches.put(squadId, new Watch());
                views.put(squadId, new View(Status.WATCHING, -1, -1, -1, -1));
            } else {
                watch.switchedOff = false;
            }
        } else if (watch != null) {
            watch.switchedOff = true;
            views.remove(squadId);
        }
    }

    /** Whether the squad is in the stance (a switch-off waiting for the next tick reads as out of it). */
    public synchronized boolean isEnabled(int squadId) {
        Watch watch = watches.get(squadId);
        return watch != null && !watch.switchedOff;
    }

    /**
     * The order bar's toggle over a selection: everyone in {@code squadIds} into the stance unless all of them already
     * are, in which case all of them out of it.
     */
    public synchronized void toggle(Collection<Integer> squadIds) {
        boolean all = !squadIds.isEmpty();
        for (int id : squadIds) all &= isEnabled(id);
        for (int id : squadIds) setEnabled(id, !all);
    }

    /** The squad's stance as last published by the system. */
    public synchronized View view(int squadId) {
        View view = views.get(squadId);
        return view == null ? View.OFF : view;
    }

    synchronized boolean isEmpty() {
        return watches.isEmpty();
    }

    /** The watches in squad-id order, as a copy the system may walk while the UI toggles. */
    synchronized List<Map.Entry<Integer, Watch>> entries() {
        return new ArrayList<>(watches.entrySet());
    }

    synchronized void remove(int squadId) {
        watches.remove(squadId);
        views.remove(squadId);
    }

    synchronized void publish(int squadId, Watch watch) {
        if (watches.get(squadId) == watch && !watch.switchedOff) views.put(squadId, watch.view());
    }
}
