package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Body;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Order;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Place;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Result;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * A story mission's finite counter-attack pool on the shared {@link PlaceCounterattack}: the mission's sites are
 * places, the whole pool this instance is given is the counter-attack's from the start, and it waits at home. A lost
 * site is observed before anything goes and retaken by the pool as one push sized to what it observed, or not at all;
 * whatever is not in the push holds home. Before this, a story pool converged on its lost site squad by squad, whatever
 * held it.
 */
final class SiteCounterattack {

    /** The least a story site is believed to take: one squad, sized against what holds it by the margin. */
    static final int STORY_FLOOR = 6;

    /** Reason suffix of a pool squad waiting at home: a standing duty a story commander may offer for manning. */
    static final String HOLD_HOME = "_HOLD_HOME";
    /** Reason suffix of a pool squad holding where it stands once home is lost: a standing duty too. */
    static final String HOLD_GROUND = "_HOLD_GROUND";

    /** One squad's order this pulse, with its reason. */
    record Duty(ObjectiveAssignment assignment, String reason) { }

    private final PlaceCounterattack counter;
    private final CompoundPlaces sites = new CompoundPlaces();
    private final int holdRadius;
    private List<String> notes = List.of();

    /** @param holdRadius the radius of the area a squad not in the push holds at home */
    SiteCounterattack(int holdRadius) {
        this.counter = new PlaceCounterattack(PlaceCounterattack.Doctrine.finitePool(STORY_FLOOR));
        this.holdRadius = holdRadius;
    }

    /** This pulse's notes, for the plan's summaries and so the command trace. */
    List<String> notes() {
        return notes;
    }

    /** Whether this squad is in the counter-attack's group. */
    boolean claims(int squadId) {
        return counter.claims(squadId);
    }

    /**
     * One pulse.
     *
     * @param include which site indices are this pool's places this pulse (a landing ground, or home, is not)
     * @param home    the cell the pool waits at and stages from, or {@code null} when it has none left
     * @param owned   the squads of this pool this pulse
     * @param all     every own squad: one standing near a site is watching it
     * @return each owned squad's duty, by squad id
     */
    Map<Integer, Duty> plan(int tick, List<ConquestCommandFacts.Compound> compounds, IntPredicate include, int[] home,
                            List<CommandSquadState> owned, List<CommandSquadState> all,
                            CommanderInfluenceSnapshot influence, CommandTopology topology, String reasonPrefix) {
        List<Place> places = sites.places(compounds, include);
        List<Body> members = new ArrayList<>();
        List<Body> free = new ArrayList<>();
        for (CommandSquadState squad : owned) {
            Body body = new Body(squad.squadId(), squad.anchorCellX(), squad.anchorCellY(), squad.aliveMembers(),
                    squad.originalMembers() > 0 ? squad.originalMembers() : squad.aliveMembers(), squad.machine(),
                    squad.demolition(), squad.localContact(), squad.assignment());
            (counter.claims(squad.squadId()) ? members : free).add(body);
        }
        List<int[]> watchers = new ArrayList<>();
        for (CommandSquadState squad : all) {
            if (squad.aliveMembers() > 0) watchers.add(new int[] {squad.anchorCellX(), squad.anchorCellY()});
        }
        Result result = counter.plan(tick, places, home, PlaceCounterattack.hostiles(influence), watchers,
                PlaceCounterattack.losses(influence), members, free, 0, owned.size(), topology);
        notes = result.notes();
        Map<Integer, Duty> duties = new TreeMap<>();
        for (CommandSquadState squad : owned) {
            Order order = result.orders().get(squad.squadId());
            if (order != null) {
                duties.put(squad.squadId(), new Duty(order.assignment(),
                        reasonPrefix + "_COUNTERATTACK_" + order.stage().name()));
            } else if (home != null) {
                duties.put(squad.squadId(), new Duty(ObjectiveAssignment.defendArea(squad.squadId(), home[0], home[1],
                        holdRadius), reasonPrefix + HOLD_HOME));
            } else {
                // Home is lost and this squad is not in the push: it holds where it stands rather than walk in alone.
                duties.put(squad.squadId(), new Duty(ObjectiveAssignment.defendArea(squad.squadId(), squad.anchorCellX(),
                        squad.anchorCellY(), holdRadius), reasonPrefix + HOLD_GROUND));
            }
        }
        return duties;
    }

    /**
     * Relay Nine's and Thunder Run's layout: site 0 is the landing, sites 1 and 2 the road gains, site 3 the hall the
     * defender holds to the last and stages from while it holds it.
     */
    Map<Integer, Duty> planRoadSites(int tick, List<ConquestCommandFacts.Compound> compounds,
                                     List<CommandSquadState> owned, List<CommandSquadState> all,
                                     CommanderInfluenceSnapshot influence, CommandTopology topology, String reasonPrefix) {
        ConquestCommandFacts.Compound hall = compounds.get(3);
        boolean held = hall.state() != CompoundState.MARINE_HELD;
        int[] home = held ? new int[] {hall.captureCellX(), hall.captureCellY()} : null;
        return plan(tick, compounds, i -> i >= 1 && (i != 3 || !held), home, owned, all, influence, topology, reasonPrefix);
    }
}
