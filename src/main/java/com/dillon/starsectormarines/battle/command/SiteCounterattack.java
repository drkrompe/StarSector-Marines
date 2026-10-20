package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Body;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Order;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Place;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Result;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A story mission's finite counter-attack pool on the shared {@link PlaceCounterattack}: the defender's sites are
 * places, the site it holds to the last is home, and the whole pool it commands is the counter-attack's from the start.
 * A lost site is observed before anything goes, retaken by the pool as one push sized to what it observed, or not at
 * all; whatever is not in the push holds home. Before this, the pool converged on the lost site squad by squad.
 */
final class SiteCounterattack {

    /** One owned squad's order this pulse, with its reason. */
    record Duty(ObjectiveAssignment assignment, String reason) { }

    private final PlaceCounterattack counter;
    private final CompoundPlaces sites = new CompoundPlaces();
    private final int homeSite;
    private final int firstSite;
    private final int holdRadius;
    private List<String> notes = List.of();

    /**
     * @param homeSite   the site the defender holds to the last, and stages from while it holds it
     * @param firstSite  the lowest site index that is the defender's at all (a landing ground before it is not)
     * @param holdRadius the radius of the area a squad holds at home
     */
    SiteCounterattack(int homeSite, int firstSite, int holdRadius) {
        this.counter = new PlaceCounterattack(PlaceCounterattack.Doctrine.finitePool(PlaceCounterattack.MIN_STRENGTH));
        this.homeSite = homeSite;
        this.firstSite = firstSite;
        this.holdRadius = holdRadius;
    }

    /** This pulse's notes, for the plan's summaries and so the command trace. */
    List<String> notes() {
        return notes;
    }

    /**
     * One pulse.
     *
     * @param owned the squads this commander moves this pulse
     * @return each owned squad's duty, by squad id
     */
    Map<Integer, Duty> plan(int tick, List<ConquestCommandFacts.Compound> compounds, List<CommandSquadState> owned,
                            List<CommandSquadState> all, CommanderInfluenceSnapshot influence, CommandTopology topology,
                            String reasonPrefix) {
        ConquestCommandFacts.Compound home = compounds.get(homeSite);
        boolean homeHeld = home.state() != CompoundState.MARINE_HELD;
        List<Place> places = sites.places(compounds, i -> i >= firstSite && (i != homeSite || !homeHeld));
        int[] keep = homeHeld ? new int[] {home.captureCellX(), home.captureCellY()} : null;
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
        Result result = counter.plan(tick, places, keep, PlaceCounterattack.hostiles(influence), watchers,
                PlaceCounterattack.losses(influence), members, free, 0, owned.size(), topology);
        notes = result.notes();
        Map<Integer, Duty> duties = new TreeMap<>();
        int homeX = home.captureCellX();
        int homeY = home.captureCellY();
        for (CommandSquadState squad : owned) {
            Order order = result.orders().get(squad.squadId());
            if (order != null) {
                duties.put(squad.squadId(), new Duty(order.assignment(),
                        reasonPrefix + "_COUNTERATTACK_" + order.stage().name()));
            } else if (homeHeld) {
                duties.put(squad.squadId(), new Duty(ObjectiveAssignment.defendArea(squad.squadId(), homeX, homeY,
                        holdRadius), reasonPrefix + "_HOLD_HOME"));
            } else {
                // Home is lost and this squad is not in the push: it holds where it stands rather than walk in alone.
                duties.put(squad.squadId(), new Duty(ObjectiveAssignment.defendArea(squad.squadId(), squad.anchorCellX(),
                        squad.anchorCellY(), holdRadius), reasonPrefix + "_HOLD_GROUND"));
            }
        }
        return duties;
    }

    /** The group's standing for a plan's phase line. */
    String phase() {
        PlaceCounterattack.Stage stage = counter.stage();
        return stage == null ? "HOLD" : "COUNTERATTACK_" + stage.name().toUpperCase(Locale.ROOT);
    }
}
