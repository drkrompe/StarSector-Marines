package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Places for one party's commander (`garrisonables.md`, parties, place directives and locked positions): the party's
 * {@link PlaceDirectives}, its locked positions, and the shared reactive planner ({@link MissionGarrisons}) hosted the
 * way {@link StoryGarrisons} hosts it for a story defender, so every order is the commander's own proposal under its
 * issuer and authority.
 *
 * <p>Each pulse the commander calls, in order: {@link #begin} with the party's own {@link PlaceSignals} (evaluates the
 * directives); for each squad it owns, {@link #own}, then {@link #lock} for a squad it numbers as a detachment of a
 * locked position, and otherwise {@link #offerDuty} for a squad on a standing duty or {@link #offerFree} for one it holds
 * free; then {@link #apply} to rewrite its proposals; and {@link #afterCommit} after commit.
 *
 * <p><b>Laws.</b> A party guards the place it was put on: a duty squad may man only the place its post stands in or
 * touches ({@link #PARTY_DUTY_RADIUS_CELLS}), and the places of an active hold directive near its post. A free squad is
 * offered only for an active hold's places, roving, so flow may stage it toward pressure there. A squad on a lock is
 * never offered: nothing reactive gives a lock back. The story share bounds what the party mans on its own initiative,
 * and each active hold adds its allowance. Belief is the party's own picture (the frame's influence). {@link #PROPERTY}
 * off is the control: nothing is offered and no hold directive opens anything; locks still stand, since they are the
 * mission's orders, not a feature on top of them. A detachment worn to half its strength or less is let go of a lock
 * that does not hold {@link PlaceDirective#toTheLast to the last}, and goes back to the commander's own duties.
 */
final class PartyPlaces {

    /** {@code -Dbattle.party.places=false} switches party manning and hold directives off (and the allied picture). */
    static final String PROPERTY = "battle.party.places";
    /** How far from its post a party's duty squad may man a place on its own initiative: the place the post stands in. */
    static final int PARTY_DUTY_RADIUS_CELLS = 0;

    static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"));
    }

    /** A locked squad's order this pulse, and the lock it holds. */
    record Locked(ObjectiveAssignment order, PlaceDirective directive) {}

    private final Faction party;
    private final PlaceDirectives directives;
    private final MissionGarrisons planner = new MissionGarrisons();
    private final Map<Integer, MissionGarrisons.Offer> offers = new TreeMap<>();
    private final Set<Integer> locked = new TreeSet<>();
    private int ownInfantry;
    private int offeredSquadPulses;
    private int shareClosedPulses;
    private int lockedSquadPulses;
    private int wornSquadPulses;

    /** A detachment at half its strength or less: a lock is held, not died in, unless it says so. */
    static boolean worn(CommandSquadState squad) {
        int established = Math.max(squad.originalMembers(), squad.aliveMembers());
        return squad.aliveMembers() * 2 <= established;
    }

    PartyPlaces(Faction party, PlaceDirectives directives) {
        this.party = party;
        this.directives = directives == null ? PlaceDirectives.none() : directives;
    }

    Faction party() { return party; }

    PlaceDirectives directives() { return directives; }

    /** Evaluates the party's directives on this pulse; returns what changed. Call first, every pulse. */
    List<PlaceDirectives.Transition> begin(PlaceSignals signals) {
        offers.clear();
        locked.clear();
        ownInfantry = 0;
        return directives.isEmpty() ? List.of() : directives.evaluate(signals);
    }

    /** Counts one of the commander's own squads toward the share; call for every squad it owns this pulse. */
    void own(CommandSquadState squad) {
        if (squad != null && squad.aliveMembers() > 0 && !squad.machine() && !squad.crew()) ownInfantry++;
    }

    /**
     * The lock detachment {@code detachment} holds this pulse, and the order that holds it, or {@code null} when no lock
     * is active. The order is the lock's defend area on the detachment's post or, with no posts and a garrisonable at
     * the lock's centre, that place's manning order; with neither, a defend area on the centre. The squad is then never
     * offered this pulse.
     */
    Locked lock(CommandSquadState squad, int detachment, GarrisonableRegistry places) {
        PlaceDirectives.Lock lock = directives.lockFor(detachment);
        if (lock == null) return null;
        PlaceDirective directive = lock.directive();
        if (!directive.toTheLast() && worn(squad)) {
            wornSquadPulses++;
            return null;
        }
        int id = squad.squadId();
        ObjectiveAssignment order;
        if (lock.post() != null) {
            order = ObjectiveAssignment.defendArea(id, lock.post().x(), lock.post().y(), directive.radius());
        } else {
            int place = places == null ? -1 : places.idAt(directive.target().x(), directive.target().y());
            order = place >= 0 ? GarrisonDirector.orderFor(id, place, places)
                    : ObjectiveAssignment.defendArea(id, directive.target().x(), directive.target().y(), directive.radius());
        }
        locked.add(id);
        offers.remove(id);
        lockedSquadPulses++;
        return new Locked(order, directive);
    }

    /**
     * Offers a squad on a standing duty at {@code (x, y)}: for the place its post stands in, and the places of every
     * active hold directive whose region lies within the planner's duty radius of the post.
     */
    void offerDuty(CommandSquadState squad, int x, int y) {
        if (!enabled() || squad == null || squad.machine() || locked.contains(squad.squadId())) return;
        List<PlaceTarget> regions = new ArrayList<>();
        for (PlaceDirective hold : directives.active(PlaceDirective.Kind.HOLD)) {
            PlaceTarget t = hold.target();
            int gap = Math.max(Math.max(Math.abs(t.x() - x) - t.radius(), Math.abs(t.y() - y) - t.radius()), 0);
            if (gap <= MissionGarrisons.DUTY_RADIUS_CELLS) regions.add(t);
        }
        offers.put(squad.squadId(), MissionGarrisons.Offer.duty(x, y, PARTY_DUTY_RADIUS_CELLS, regions));
    }

    /** Offers a squad the commander holds free, roving for the places of the active hold directives; none, no offer. */
    void offerFree(CommandSquadState squad) {
        if (!enabled() || squad == null || squad.machine() || locked.contains(squad.squadId())) return;
        List<PlaceTarget> regions = new ArrayList<>();
        for (PlaceDirective hold : directives.active(PlaceDirective.Kind.HOLD)) regions.add(hold.target());
        if (regions.isEmpty()) return;
        offers.put(squad.squadId(), MissionGarrisons.Offer.within(regions));
    }

    /** The offer this pulse holds for a squad, or {@code null}; for tests. */
    MissionGarrisons.Offer offer(int squadId) {
        return offers.get(squadId);
    }

    /** How many may hold a place at once this pulse: the story share of the party's own infantry, plus active holds' allowance. */
    int holderCap() {
        int cap = StoryGarrisons.holderCap(ownInfantry);
        for (PlaceDirective hold : directives.active(PlaceDirective.Kind.HOLD)) cap += hold.allowance();
        return cap;
    }

    /**
     * Plans this pulse's offers over the party's own frame and returns the commander's proposals with the manning in
     * place of its own for the squads it names. {@code places} may be {@code null}.
     */
    List<CommandProposal> apply(CommandFrame frame, GarrisonableRegistry places, List<CommandProposal> proposals,
                                String issuer) {
        if (!enabled() || places == null) return proposals;
        int cap = holderCap();
        offeredSquadPulses += offers.size();
        if (!offers.isEmpty() && cap == 0) shareClosedPulses++;
        Map<Integer, MissionGarrisons.Manning> manning = planner.plan(frame, places, offers, issuer, cap);
        return manning.isEmpty() ? proposals : MissionGarrisons.rewrite(proposals, manning, frame.squads());
    }

    /** Gives and ends machine guards; call from the commander's afterCommit. */
    void afterCommit(CommandEffects effects) {
        planner.afterCommit(effects, party);
    }

    /** The planner's figures, this party's offers, and its directives' and locks' counts; for evidence. */
    Map<String, Integer> figures() {
        Map<String, Integer> out = ReactiveDefenceSource.figures(planner);
        out.put("offeredSquadPulses", offeredSquadPulses);
        out.put("shareClosedPulses", shareClosedPulses);
        out.put("directiveActivations", directives.activations());
        out.put("directiveReleases", directives.releases());
        out.put("lockedSquadPulses", lockedSquadPulses);
        out.put("wornSquadPulses", wornSquadPulses);
        return out;
    }
}
