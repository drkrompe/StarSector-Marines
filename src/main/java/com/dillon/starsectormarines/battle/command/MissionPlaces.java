package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Body;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Hostile;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Place;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Status;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.HeldPlaces;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The places a procedural mission's defender holds and can lose, as {@link PlaceCounterattack} places
 * (`garrisonables.md`, places not forts): the mission's own objective places — the Raid target, the unfinished Sabotage
 * sites, the Extraction source — and its <b>key buildings</b>, the buildings its born garrisons hold. Only places that
 * matter to the mission are places: a key building counts beside an objective (or, on Assault, which has none, every
 * strongpoint does), and an objective the mission has finished with (a target seized, a site destroyed, a payload
 * gone) is no place at all.
 *
 * <p><b>How a place stands is own force and belief, never truth.</b> Nothing on these maps keeps a capture record, so a
 * place is <em>lost</em> once hostile movers have been believed on its ground for a couple of pulses with no own squad
 * on it, <em>being won back</em> while own squads stand on it with the holders still believed there, and whole again
 * once nothing hostile has been believed on it for a few pulses. A place whose holders have moved on is not one to
 * retake: it is the ordinary defence's again.
 *
 * <p>Stateful only in what it remembers of the side's own places; one instance per commander.
 */
final class MissionPlaces {

    /** What a mission's objective place is worth to its side by itself. UNTUNED. */
    static final float OBJECTIVE_WORTH = 2f;
    /** What a key building is worth by itself; one the mission says must be held is worth more. UNTUNED. */
    static final float BUILDING_WORTH = 1f;
    static final float MUST_HOLD_WORTH = 0.5f;
    /** A key building this near an objective (cells, box to cell) is part of it. UNTUNED. */
    static final int KEY_REACH_CELLS = 30;
    /** The ground of a place: never smaller, never larger. */
    static final int MIN_RADIUS_CELLS = CompoundPlaces.MIN_RADIUS_CELLS;
    static final int MAX_RADIUS_CELLS = CompoundPlaces.MAX_RADIUS_CELLS;
    /** An own squad this far outside a place's box (or this near a point place) stands on it. */
    static final int ON_CELLS = 2;
    /** Half the box of a place that is a point, not a building. */
    static final int POINT_HALF_CELLS = 4;
    /** Believed hostile weight on a place's ground that holds it: a fire team, not a straggler. UNTUNED. */
    static final float HOLD_WEIGHT = 2f;
    /** Pulses held before a place is lost, and pulses quiet before a lost place is whole again. UNTUNED. */
    static final int TURN_PULSES = 2;
    static final int CLEAR_PULSES = 3;
    /** Largest radius of the area each member of a launched group holds at the place. */
    static final int HOLD_RADIUS_CELLS = 6;

    /** A building a born garrison of the side holds, frozen off the tactical map. */
    record Building(int anchorX, int anchorY, int left, int top, int right, int bottom, boolean mustHold) { }

    /**
     * A place that matters to the mission this pulse.
     *
     * @param key    stable identity and the label the notes use
     * @param x      its centre, where a launched group holds
     * @param radius its ground: hostiles within it hold the place
     */
    record Site(String key, int x, int y, int left, int top, int right, int bottom, int radius, float worth) { }

    private static final class State {
        boolean lost;
        int turnPulses;
        int clearPulses;
    }

    private final Map<String, State> states = new TreeMap<>();
    private final List<String> changes = new ArrayList<>();

    /** What turned or came whole at the last look, for the command trace. */
    List<String> changes() {
        return changes;
    }

    /** The side's key buildings: every posted node its born garrisons hold, as the defender is disclosed them. */
    static List<Building> keyBuildings(BattleView sim, Faction side) {
        List<Building> out = new ArrayList<>();
        TacticalMap map = sim.getTacticalMap();
        if (map == null) return out;
        for (TacticalNode node : map.all()) {
            if (node.defaultGuard != side || HeldPlaces.isUnposted(node)) continue;
            out.add(new Building(node.anchorX, node.anchorY, node.left, node.top, node.right, node.bottom,
                    node.mustHold));
        }
        return out;
    }

    /** A key building as a place. */
    static Site building(Building b, float worth) {
        int half = (int) Math.ceil(Math.hypot(b.right() - b.left(), b.bottom() - b.top()) / 2.0)
                + CompoundGround.REACH_CELLS;
        return new Site("building " + b.anchorX() + "," + b.anchorY(), b.anchorX(), b.anchorY(), b.left(), b.top(),
                b.right(), b.bottom(), clampRadius(half), worth + (b.mustHold() ? MUST_HOLD_WORTH : 0f));
    }

    /** A mission objective at a cell as a place: the building that holds the cell, if one does, is its ground. */
    static Site objective(String key, int x, int y, List<Building> buildings) {
        Building in = containing(buildings, x, y);
        if (in == null) {
            return new Site(key, x, y, x - POINT_HALF_CELLS, y - POINT_HALF_CELLS, x + POINT_HALF_CELLS,
                    y + POINT_HALF_CELLS, MIN_RADIUS_CELLS, OBJECTIVE_WORTH);
        }
        Site ground = building(in, 0f);
        return new Site(key, x, y, in.left(), in.top(), in.right(), in.bottom(), ground.radius(), OBJECTIVE_WORTH);
    }

    /**
     * The mission's places: its objectives, and the key buildings within {@link #KEY_REACH_CELLS} of any of them that do
     * not already hold one.
     */
    static List<Site> around(List<Site> objectives, List<Building> buildings) {
        List<Site> out = new ArrayList<>(objectives);
        for (Building b : buildings) {
            boolean near = false;
            boolean holds = false;
            for (Site o : objectives) {
                if (b.left() <= o.x() && o.x() <= b.right() && b.top() <= o.y() && o.y() <= b.bottom()) holds = true;
                if (gap(b, o.x(), o.y()) <= KEY_REACH_CELLS) near = true;
            }
            if (near && !holds) out.add(building(b, BUILDING_WORTH));
        }
        return out;
    }

    /** Every key building as a place: a mission with no objective place of its own (Assault). */
    static List<Site> buildings(List<Building> buildings) {
        List<Site> out = new ArrayList<>(buildings.size());
        for (Building b : buildings) out.add(building(b, BUILDING_WORTH));
        return out;
    }

    /**
     * This pulse's places, by belief and own force.
     *
     * @param hostiles believed hostile movers, never emplacements
     * @param own      every own squad's cell this pulse (anchor), alive
     */
    List<Place> places(List<Site> sites, List<Hostile> hostiles, List<int[]> own) {
        List<Place> out = new ArrayList<>(sites.size());
        changes.clear();
        for (Site site : sites) {
            State state = states.computeIfAbsent(site.key(), k -> new State());
            float seen = 0f;
            for (Hostile h : hostiles) {
                if (Math.hypot(h.x() - site.x(), h.y() - site.y()) <= site.radius()) seen += h.weight();
            }
            boolean ownOn = false;
            for (int[] cell : own) {
                if (cell[0] >= site.left() - ON_CELLS && cell[0] <= site.right() + ON_CELLS
                        && cell[1] >= site.top() - ON_CELLS && cell[1] <= site.bottom() + ON_CELLS) {
                    ownOn = true;
                    break;
                }
            }
            if (!state.lost) {
                state.turnPulses = seen >= HOLD_WEIGHT && !ownOn ? state.turnPulses + 1 : 0;
                if (state.turnPulses >= TURN_PULSES) {
                    state.lost = true;
                    state.clearPulses = 0;
                    changes.add(String.format(Locale.ROOT, "mission place %s is lost: %.0f believed on it, none of ours",
                            site.key(), seen));
                }
            } else {
                state.clearPulses = seen <= 0f ? state.clearPulses + 1 : 0;
                if (state.clearPulses >= CLEAR_PULSES) {
                    state.lost = false;
                    state.turnPulses = 0;
                    changes.add("mission place " + site.key() + " is whole again: nobody hostile is believed on it");
                }
            }
            Status status = !state.lost ? Status.HELD : ownOn ? Status.LOSING : Status.LOST;
            int x = site.x();
            int y = site.y();
            int hold = Math.max(2, Math.min(HOLD_RADIUS_CELLS,
                    Math.min(site.right() - site.left(), site.bottom() - site.top()) / 2));
            out.add(new Place(site.key(), x, y, site.radius(), status, site.worth(), false,
                    status == Status.LOSING, (members, topology) -> hold(x, y, hold, members)));
        }
        return out;
    }

    /** Whether the place under this key was lost at the last look. */
    boolean lost(String key) {
        State state = states.get(key);
        return state != null && state.lost;
    }

    static Map<Integer, ObjectiveAssignment> hold(int x, int y, int radius, List<Body> members) {
        Map<Integer, ObjectiveAssignment> out = new TreeMap<>();
        for (Body body : members) out.put(body.squadId(), ObjectiveAssignment.defendArea(body.squadId(), x, y, radius));
        return out;
    }

    private static Building containing(List<Building> buildings, int x, int y) {
        for (Building b : buildings) {
            if (b.left() <= x && x <= b.right() && b.top() <= y && y <= b.bottom()) return b;
        }
        return null;
    }

    private static int gap(Building b, int x, int y) {
        return Math.max(0, Math.max(Math.max(b.left() - x, x - b.right()), Math.max(b.top() - y, y - b.bottom())));
    }

    private static int clampRadius(int r) {
        return Math.max(MIN_RADIUS_CELLS, Math.min(MAX_RADIUS_CELLS, r));
    }
}
