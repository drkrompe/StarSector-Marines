package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ruins' leaf doors as they stand now ({@code ruins-expedition.md}, The ruins' doors are leaf doors): each door the
 * ground laid, with the stance it was laid in, and each door added since, every one's stance as the battle has left it.
 * A {@link RuinsDelve.LeafDoor} records only how its door was laid; this is what the drawing reads.
 *
 * <p>Presentation only: passage and sight are the grid's, and whoever opens or shuts a door's cells
 * ({@code MapEditor}) also tells this. The derelict watch does, for the dead doors its hall frames force and for its
 * bulkheads, laid {@link RuinsDelve.DoorStance#SEALED SEALED} and {@linkplain #open opened} at the hunt.
 */
public final class RuinsDoorStates {

    private final Map<RuinsDelve.LeafDoor, RuinsDelve.DoorStance> stances = new LinkedHashMap<>();
    /** The span a door was torn out to, where it was torn wider than it was built. */
    private final Map<RuinsDelve.LeafDoor, RuinsDelve.Area> torn = new HashMap<>();
    private int version;

    /** The doors the ground laid, each in the stance it was laid in. */
    public RuinsDoorStates(List<RuinsDelve.LeafDoor> laid) {
        for (RuinsDelve.LeafDoor door : laid) stances.put(door, door.stance());
    }

    /** Every door, those the ground laid first and those added since after them, in the order they came. */
    public List<RuinsDelve.LeafDoor> doors() {
        return Collections.unmodifiableList(new ArrayList<>(stances.keySet()));
    }

    /** A door's stance now; null for a door this does not hold. */
    public RuinsDelve.DoorStance stanceOf(RuinsDelve.LeafDoor door) {
        return stances.get(door);
    }

    /** Adds a door made after the ground was laid (a bulkhead), standing as {@code stance}. */
    public void add(RuinsDelve.LeafDoor door, RuinsDelve.DoorStance stance) {
        if (stances.containsKey(door)) throw new IllegalArgumentException("door already held: " + door.span());
        stances.put(door, stance);
        version++;
    }

    /** A dead door torn open: it stands forced from now on. */
    public void force(RuinsDelve.LeafDoor door) {
        set(door, RuinsDelve.DoorStance.FORCED);
    }

    /**
     * A door torn out to {@code widened}, its jambs with it: it stands {@link RuinsDelve.DoorStance#TORN TORN} at that
     * span from now on. {@code widened} lies on the door's own line and takes in every cell of its span.
     */
    public void tear(RuinsDelve.LeafDoor door, RuinsDelve.Area widened) {
        require(door);
        RuinsDelve.Area span = door.span();
        boolean onLine = door.alongX() ? widened.bottom() == span.bottom() && widened.top() == span.top()
                : widened.left() == span.left() && widened.right() == span.right();
        boolean covers = widened.left() <= span.left() && widened.right() >= span.right()
                && widened.bottom() <= span.bottom() && widened.top() >= span.top();
        if (!onLine || !covers) {
            throw new IllegalArgumentException("a torn span " + widened + " must widen " + span + " along its line");
        }
        torn.put(door, widened);
        stances.put(door, RuinsDelve.DoorStance.TORN);
        version++;
    }

    /** The cells a door stands across now: its span, or the wider one it was torn out to. */
    public RuinsDelve.Area spanOf(RuinsDelve.LeafDoor door) {
        RuinsDelve.Area widened = torn.get(door);
        return widened != null ? widened : door.span();
    }

    /** A sealed door opened: it answers from now on, parting for whoever comes through. */
    public void open(RuinsDelve.LeafDoor door) {
        if (require(door) != RuinsDelve.DoorStance.SEALED) throw new IllegalArgumentException("only a sealed door opens");
        set(door, RuinsDelve.DoorStance.ANSWERS);
    }

    /** Counts every change, so a reader can tell its copy has gone stale without comparing stances. */
    public int version() {
        return version;
    }

    private void set(RuinsDelve.LeafDoor door, RuinsDelve.DoorStance stance) {
        if (require(door) == stance) return;
        stances.put(door, stance);
        version++;
    }

    private RuinsDelve.DoorStance require(RuinsDelve.LeafDoor door) {
        RuinsDelve.DoorStance now = stances.get(door);
        if (now == null) throw new IllegalArgumentException("door not held: " + door.span());
        return now;
    }
}
