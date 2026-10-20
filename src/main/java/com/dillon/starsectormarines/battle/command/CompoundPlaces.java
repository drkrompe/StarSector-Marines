package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Body;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Place;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Status;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * The compounds a defending side holds, as {@link PlaceCounterattack} places: a compound objective is lost when the
 * marines have taken it and is being won back while it is contested after that; a compound merely contested has not been
 * lost and is still the ordinary defence's to fight for. Its retake is the whole group holding its capture cell.
 *
 * <p>Stateful only in what it remembers of the side's own places: which compounds it has lost and not yet won back.
 */
final class CompoundPlaces {

    /** What a compound objective is worth to its side by itself. UNTUNED. */
    static final float WORTH = 1f;
    /** A compound the mission says must be held is worth this much more. UNTUNED. */
    static final float MUST_HOLD_WORTH = 0.5f;
    /** The least and most a compound's ground reaches from its capture cell. */
    static final int MIN_RADIUS_CELLS = 16;
    static final int MAX_RADIUS_CELLS = AssaultMuster.CONTACT_RADIUS_CELLS;
    /** Radius of the area each member holds at the capture cell when launched. */
    static final int HOLD_RADIUS_CELLS = 4;

    private final Set<Integer> lost = new TreeSet<>();

    /** The place key of the compound at this index. */
    static String key(int index) {
        return "compound " + index;
    }

    /**
     * The compounds as places, by their index in {@code compounds}.
     *
     * @param include which indices are places at all (the keep, home or a landing ground is not one to retake)
     */
    List<Place> places(List<ConquestCommandFacts.Compound> compounds, IntPredicate include) {
        List<Place> out = new ArrayList<>();
        for (int i = 0; i < compounds.size(); i++) {
            ConquestCommandFacts.Compound compound = compounds.get(i);
            if (compound.state() == CompoundState.MARINE_HELD) lost.add(i);
            if (compound.state() == CompoundState.DEFENDER_HELD) lost.remove(i);
            if (!include.test(i)) continue;
            Status status = compound.state() == CompoundState.MARINE_HELD ? Status.LOST
                    : lost.contains(i) ? Status.LOSING : Status.HELD;
            TacticalNode node = compound.node();
            int x = compound.captureCellX() >= 0 ? compound.captureCellX() : node.centerX();
            int y = compound.captureCellY() >= 0 ? compound.captureCellY() : node.centerY();
            // The building's half-diagonal and its apron's reach: a yard in front of its door is its ground.
            int half = (int) Math.ceil(Math.hypot(node.right - node.left, node.bottom - node.top) / 2.0)
                    + CompoundGround.REACH_CELLS;
            int radius = Math.max(MIN_RADIUS_CELLS, Math.min(MAX_RADIUS_CELLS, half));
            float worth = WORTH + (node.mustHold ? MUST_HOLD_WORTH : 0f);
            boolean capturing = status == Status.LOSING;
            out.add(new Place(key(i), x, y, radius, status, worth, false, capturing,
                    (members, topology) -> hold(x, y, members)));
        }
        return out;
    }

    /** The index of the compound a place key names, or -1 for a key that is not a compound's. */
    static int index(String key) {
        if (key == null || !key.startsWith("compound ")) return -1;
        return Integer.parseInt(key.substring("compound ".length()));
    }

    private static Map<Integer, ObjectiveAssignment> hold(int x, int y, List<Body> members) {
        Map<Integer, ObjectiveAssignment> out = new TreeMap<>();
        for (Body body : members) out.put(body.squadId(), ObjectiveAssignment.defendArea(body.squadId(), x, y,
                HOLD_RADIUS_CELLS));
        return out;
    }
}
