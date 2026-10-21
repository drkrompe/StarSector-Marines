package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.MissionPlaces.Site;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Body;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Order;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Place;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Status;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A procedural mission defender's counter-attack (`garrisonables.md`, mission defenders; `conquest-command.md`, a place
 * the defender loses): the Raid, Assault, Sabotage and Extraction defenders retake the places that matter to their
 * mission ({@link MissionPlaces}) through the shared {@link PlaceCounterattack} — probe, size from what holds it, muster,
 * launch together or stand down — and nothing else.
 *
 * <p><b>Reasonable, by doctrine.</b> The mission decides first: the counter-attack recruits only squads the mission holds
 * in its <em>reserve</em>, never a post (the Raid target's guard, a Sabotage site post, an Assault routine area or
 * responder, the Extraction source guard), an interdicting squad, an authored garrison, a crew or anybody another owner
 * holds; quiet authored garrisons away from the fight may sortie into a decided push. Its group never holds more than
 * {@link #share} of the side's infantry, it sizes a place at parity with what it has observed holds it ({@link #margin})
 * and goes once it can raise half of that ({@link #commitShare}), and the side has {@link #maxLaunches} pushes a battle:
 * after that, and after two failures on one place, the ordinary defence is all there is. A squad in the group is the
 * counter-attack's until it is let go; the mission plans everybody else.
 *
 * <p><b>Triggered.</b> A mission may also author "counter-attack this place when ..." as a {@link PlaceDirective} of kind
 * {@link PlaceDirective.Kind#COUNTER_ATTACK} (`garrisonables.md`, place directives). This class is the adapter: it holds
 * the party's {@link PlaceDirectives}, evaluates them each pulse on the commander's {@link PlaceSignals}, and offers every
 * active one to the same planner as a lost place of its own, worth more than any the mission reads for itself and
 * standing in for those inside its target. The trigger decides when; the doctrine still decides with what and whether, so
 * an authored counter-attack against more than the side can raise is observed and stood down like any other.
 *
 * <p>{@code -Dbattle.mission.counterattack=false} (or the shared {@code -Dbattle.counterattack=false}) is the
 * same-build control for the untriggered form, {@code -Dbattle.mission.counterattack.triggered=false} for the authored
 * one; the knobs are {@code -Dbattle.mission.counterattack.<name>}.
 */
final class MissionCounterattack {

    /** {@code -Dbattle.mission.counterattack=false} switches the procedural missions' counter-attack off. */
    static final String PROPERTY = "battle.mission.counterattack";
    /** The reason a group member's order carries: this, then its stage ({@code COUNTERATTACK_LAUNCHED}). */
    static final String REASON_PREFIX = "COUNTERATTACK_";
    /** The reason a squad the counter-attack let go carries for the pulse it is handed back. */
    static final String RELEASED_REASON = "MOBILE_RESERVE_HELD";

    /**
     * The most of the side's infantry, as a share of its squads (the mobile pool and the authored garrisons together),
     * the group may hold: half. Evidence-tuned (`conquest-command.md`, procedural missions).
     */
    static final float SHARE = 0.5f;
    /**
     * Marines brought for each observed holder: parity. A mission's holders are a squad passing through or standing on
     * its objective in the open, not a consolidated, dug-in hold, and at Conquest's two to one these defenders never
     * had the means to go at all. Evidence-tuned.
     */
    static final float MARGIN = 1f;
    /** The share of what a place takes the side must raise to go: half. Evidence-tuned. */
    static final float COMMIT_SHARE = 0.5f;
    /** The least a mission place is believed to take: one squad. */
    static final int FLOOR = SiteCounterattack.STORY_FLOOR;
    /** Pushes a side may launch in one battle. UNTUNED. */
    static final int MAX_LAUNCHES = 2;

    static volatile float share = knob("share", SHARE);
    static volatile int floor = (int) knob("floor", FLOOR);
    /** Zero is the shared counter-attack's margin. */
    static volatile float margin = knob("margin", MARGIN);
    static volatile int maxLaunches = (int) knob("maxLaunches", MAX_LAUNCHES);
    /** Zero is the shared counter-attack's commit share. */
    static volatile float commitShare = knob("commitShare", COMMIT_SHARE);
    /** Whether quiet authored garrisons may sortie into a decided counter-attack; off is the mobile-reserve-only control. */
    static volatile boolean sortie = knob("sortie", 1f) > 0f;

    /**
     * A garrison with a believed hostile mover this close is not quiet and does not sortie: the safe-contact radius of a
     * staging cell, tighter than Conquest's forty on maps a quarter its size. Evidence-tuned.
     */
    static final int SORTIE_QUIET_CELLS = AssaultMuster.SAFE_CONTACT_CELLS;
    /** A garrison further than this from the place does not sortie to it. UNTUNED. */
    static final int SORTIE_REACH_CELLS = 80;
    static volatile int sortieQuietCells = (int) knob("sortieQuietCells", SORTIE_QUIET_CELLS);
    static volatile int sortieReachCells = (int) knob("sortieReachCells", SORTIE_REACH_CELLS);
    /** The issuer suffix of the procedural missions' authored garrisons ({@code BattleSetup}). */
    static final String SETUP_GARRISON_SUFFIX = "-setup-garrison";

    /** {@code -Dbattle.mission.counterattack.triggered=false} switches authored counter-attack directives off. */
    static final String TRIGGERED_PROPERTY = PROPERTY + ".triggered";
    /** The key prefix of a place an authored directive names: this, then the directive's id. */
    static final String DIRECTIVE_KEY_PREFIX = "directive ";
    /** What an authored counter-attack's place is worth: more than any objective, because the mission said so. */
    static final float AUTHORED_WORTH = 2 * MissionPlaces.OBJECTIVE_WORTH;

    private static volatile Boolean forced;

    private static float knob(String name, float fallback) {
        String value = System.getProperty(PROPERTY + "." + name);
        return value == null ? fallback : Float.parseFloat(value);
    }

    /** Whether the procedural missions counter-attack: both this switch and the shared one. */
    static boolean enabled() {
        Boolean f = forced;
        if (f != null) return f;
        return PlaceCounterattack.isEnabled() && !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"));
    }

    /** Whether authored counter-attack directives are read: both this switch and the shared one. */
    static boolean triggeredEnabled() {
        return PlaceCounterattack.isEnabled()
                && !"false".equalsIgnoreCase(System.getProperty(TRIGGERED_PROPERTY, "true"));
    }

    /** Evidence seam: forces the switch for the rest of this JVM; {@code null} reads the properties again. */
    static void setEnabledForEvidence(Boolean on) {
        forced = on;
    }

    /** One group member's order this pulse, with its reason. */
    record Duty(ObjectiveAssignment assignment, String reason) { }

    /**
     * One pulse.
     *
     * @param duties    every squad in the group after the pulse, by id
     * @param recruited reserve squads taken into the group this pulse: the mission's own proposal for them is replaced
     * @param released  squads let go this pulse: handed back to the mission's reserve
     * @param notes     the counter-attack's notes, for the plan's summaries and so the command trace
     */
    record Result(Map<Integer, Duty> duties, List<Integer> recruited, List<Integer> released, List<String> notes) {
        static final Result NONE = new Result(Map.of(), List.of(), List.of(), List.of());
    }

    private final PlaceCounterattack counter;
    private final MissionPlaces places = new MissionPlaces();
    private final Set<Integer> held = new TreeSet<>();
    /** Garrisons recruited this pulse, by squad id, with the issuer each is taken over from in {@link #afterCommit}. */
    private Map<Integer, String> handoffs = Map.of();
    private PlaceDirectives directives = PlaceDirectives.none();

    MissionCounterattack() {
        this(PlaceCounterattack.Doctrine.mission(share, floor, margin, commitShare, maxLaunches));
    }

    MissionCounterattack(PlaceCounterattack.Doctrine doctrine) {
        this.counter = new PlaceCounterattack(doctrine);
    }

    /**
     * The mission's place directives, of which this party's counter-attack ones are read; the commander's to call once,
     * at setup. With the triggered switch off the book is empty.
     */
    void directives(List<PlaceDirective> mission, Faction party) {
        directives = PlaceDirectives.forParty(triggeredEnabled() ? mission : List.of(), party);
    }

    /** The party's directive book, for tests and scenes. */
    PlaceDirectives directives() {
        return directives;
    }

    /** Whether the commander plans a counter-attack at all: the untriggered form, or an authored directive to read. */
    boolean active() {
        return enabled() || !directives.isEmpty() && triggeredEnabled();
    }

    /** The squads the group held after the last plan: the mission leaves these out of its own planning. */
    Set<Integer> claimed() {
        return Set.copyOf(held);
    }

    /** The shared planner, for tests and scenes. */
    PlaceCounterattack counter() {
        return counter;
    }

    /** The places' standing, for tests and scenes. */
    MissionPlaces places() {
        return places;
    }

    /**
     * One pulse.
     *
     * @param sites      the places that matter to the mission this pulse
     * @param members    the group's squads as the mission found them (those of {@link #claimed()} still its own)
     * @param reserve    squads the mission holds in reserve this pulse, which the group may recruit
     * @param poolSquads the mission's mobile pool, in squads, the group's included
     * @param all        every own squad: one standing near a place watches it, one on it holds it
     * @param signals    the party's pulse as its place directives read it; {@code null} when it has none
     */
    Result plan(int tick, List<Site> sites, List<CommandSquadState> members, List<CommandSquadState> reserve,
                int poolSquads, List<CommandSquadState> all, CommanderInfluenceSnapshot influence,
                CommandTopology topology, PlaceSignals signals) {
        List<PlaceCounterattack.Hostile> hostiles = PlaceCounterattack.hostiles(influence);
        List<int[]> own = new ArrayList<>();
        for (CommandSquadState squad : all) {
            if (squad.aliveMembers() > 0) own.add(new int[] {squad.anchorCellX(), squad.anchorCellY()});
        }
        List<Place> list = new ArrayList<>(places.places(enabled() ? sites : List.of(), hostiles, own));
        List<String> directiveNotes = new ArrayList<>();
        List<Place> authored = triggered(signals, directiveNotes);
        if (!authored.isEmpty()) {
            // A mission place inside an authored target is that directive's: one ground, one place.
            List<PlaceTarget> targets = new ArrayList<>();
            for (PlaceDirective directive : directives.active(PlaceDirective.Kind.COUNTER_ATTACK)) {
                targets.add(directive.target());
            }
            list.removeIf(place -> targets.stream().anyMatch(target -> target.contains(place.x(), place.y())));
            list.addAll(authored);
        }
        List<Body> memberBodies = new ArrayList<>();
        for (CommandSquadState squad : members) memberBodies.add(body(squad));
        List<Body> free = new ArrayList<>();
        for (CommandSquadState squad : reserve) {
            if (!squad.machine() && !squad.crew() && !squad.demolition() && !squad.moraleBroken()) free.add(body(squad));
        }
        int garrisonSquads = 0;
        for (CommandSquadState squad : all) if (squad.aliveMembers() > 0 && garrisonIssuer(squad) != null) garrisonSquads++;
        Map<Integer, String> sortieIssuers = sortie ? sortieGarrisons(list, sites, all, hostiles) : Map.of();
        List<Body> sortieBodies = new ArrayList<>();
        for (CommandSquadState squad : all) if (sortieIssuers.containsKey(squad.squadId())) sortieBodies.add(body(squad));
        PlaceCounterattack.Result result = counter.plan(tick, list, null, hostiles, own,
                PlaceCounterattack.losses(influence), memberBodies, free, sortieBodies, 0, poolSquads + garrisonSquads,
                topology);
        Map<Integer, Duty> duties = new TreeMap<>();
        Map<Integer, String> handing = new TreeMap<>();
        for (Map.Entry<Integer, Order> entry : result.orders().entrySet()) {
            int id = entry.getKey();
            String issuer = sortieIssuers.get(id);
            if (issuer != null && result.recruited().contains(id)) {
                // A garrison is its own authority's until it is handed over; from the next pulse it is the group's.
                handing.put(id, issuer);
                continue;
            }
            duties.put(id, new Duty(entry.getValue().assignment(), REASON_PREFIX + entry.getValue().stage().name()));
        }
        handoffs = handing;
        held.clear();
        held.addAll(duties.keySet());
        held.addAll(handing.keySet());
        List<Integer> released = new ArrayList<>();
        for (int id : result.released()) if (!duties.containsKey(id)) released.add(id);
        List<Integer> recruited = new ArrayList<>();
        for (int id : result.recruited()) if (duties.containsKey(id)) recruited.add(id);
        List<String> notes = new ArrayList<>(directiveNotes);
        notes.addAll(places.changes());
        notes.addAll(result.notes());
        if (!handing.isEmpty()) {
            notes.add("counter-attack for " + counter.placeKey() + " draws garrisons out to sortie: squads "
                    + new ArrayList<>(handing.keySet()));
        }
        return new Result(duties, recruited, released, notes);
    }

    /**
     * The adapter onto the place-directive seam: evaluates the book on this pulse's signals and returns a place for every
     * active counter-attack directive, keyed {@link #DIRECTIVE_KEY_PREFIX} and its id, centred on its target with the
     * target as its ground (at least {@link MissionPlaces#ON_CELLS}). Its trigger has said it is to be retaken, so it
     * stands lost, or losing while the party's own squads are in it; a launched group holds it as a mission place is
     * held. A directive released (by default once the party holds the target again) is simply no longer offered.
     */
    List<Place> triggered(PlaceSignals signals, List<String> notes) {
        if (directives.isEmpty() || signals == null) return List.of();
        for (PlaceDirectives.Transition change : directives.evaluate(signals)) {
            if (change.kind() != PlaceDirective.Kind.COUNTER_ATTACK) continue;
            notes.add("place directive " + change.id() + " is " + change.to().name().toLowerCase(Locale.ROOT) + " ("
                    + change.trigger() + ")");
        }
        List<Place> out = new ArrayList<>();
        for (PlaceDirective directive : directives.active(PlaceDirective.Kind.COUNTER_ATTACK)) {
            PlaceTarget target = directive.target();
            PlaceHold hold = signals.hold(target);
            Status status = hold == PlaceHold.OWN || hold == PlaceHold.CONTESTED ? Status.LOSING : Status.LOST;
            int x = target.x();
            int y = target.y();
            int ground = Math.max(MissionPlaces.ON_CELLS, target.radius());
            int within = Math.max(2, Math.min(MissionPlaces.HOLD_RADIUS_CELLS, target.radius()));
            out.add(new Place(DIRECTIVE_KEY_PREFIX + directive.id(), x, y, ground, status, AUTHORED_WORTH, false,
                    status == Status.LOSING, (members, topology) -> MissionPlaces.hold(x, y, within, members)));
        }
        return out;
    }

    /** Hands this pulse's sortied garrisons to the commander; call from its afterCommit. */
    void afterCommit(CommandEffects effects, String strategyId) {
        for (Map.Entry<Integer, String> entry : handoffs.entrySet()) {
            effects.takeOverSquad(entry.getKey(), entry.getValue(), strategyId, "garrison sorties into a counter-attack");
        }
        handoffs = Map.of();
    }

    /**
     * Authored garrisons that may sortie into a decided counter-attack, with the issuer each is held by: alive, infantry,
     * not in a fight ({@link #fighting}), with no believed hostile mover within {@link #SORTIE_QUIET_CELLS}, within
     * {@link #SORTIE_REACH_CELLS} of the place the group stands for (of any turned place while it stands for none), and
     * not standing on an objective place or a place that has turned: an objective's own garrison keeps its post, a key
     * building's quiet garrison may leave it (the reactive defence re-mans it under pressure, as for any building whose
     * garrison is gone). What a Raid, Assault, Sabotage or Extraction defender has left when a place turns is mostly its
     * garrisons in the buildings away from the fight; without them a counter-attack could raise nothing.
     */
    private Map<Integer, String> sortieGarrisons(List<Place> list, List<Site> sites, List<CommandSquadState> all,
                                                 List<PlaceCounterattack.Hostile> hostiles) {
        String key = counter.placeKey();
        List<Place> targets = new ArrayList<>();
        for (Place place : list) {
            if (place.turned() && (key == null || place.key().equals(key))) targets.add(place);
        }
        Map<Integer, String> out = new TreeMap<>();
        if (targets.isEmpty()) return out;
        for (CommandSquadState squad : all) {
            String issuer = garrisonIssuer(squad);
            if (issuer == null || squad.aliveMembers() < 2 || squad.machine() || squad.crew() || squad.demolition()
                    || squad.moraleBroken() || fighting(squad)) continue;
            int x = squad.anchorCellX();
            int y = squad.anchorCellY();
            boolean inReach = false;
            for (Place place : targets) inReach |= Math.hypot(x - place.x(), y - place.y()) <= sortieReachCells;
            if (!inReach) continue;
            boolean onSite = false;
            for (Site site : sites) {
                // An objective's own garrison keeps its post, and so does one whose own building has turned.
                if ((site.worth() >= MissionPlaces.OBJECTIVE_WORTH || places.lost(site.key()))
                        && x >= site.left() - MissionPlaces.ON_CELLS && x <= site.right() + MissionPlaces.ON_CELLS
                        && y >= site.top() - MissionPlaces.ON_CELLS && y <= site.bottom() + MissionPlaces.ON_CELLS) {
                    onSite = true;
                    break;
                }
            }
            if (onSite) continue;
            boolean quiet = true;
            for (PlaceCounterattack.Hostile hostile : hostiles) {
                if (Math.hypot(hostile.x() - x, hostile.y() - y) <= sortieQuietCells) {
                    quiet = false;
                    break;
                }
            }
            if (quiet) out.put(squad.squadId(), issuer);
        }
        return out;
    }

    /** The issuer of an authored garrison's hold, or {@code null} for a squad that is not one. */
    private static String garrisonIssuer(CommandSquadState squad) {
        CommandDirective directive = squad.directive();
        if (directive == null || directive.authority() != CommandAuthority.GARRISON || directive.issuer() == null
                || !directive.issuer().endsWith(SETUP_GARRISON_SUFFIX)) return null;
        return directive.issuer();
    }

    /**
     * The mission's proposals with the counter-attack's in place of its own for every squad in the group, and a reserve
     * claim (its retake or staging order withdrawn) for every squad let go this pulse. A member whose order is unchanged
     * keeps it without breaking its stability.
     */
    static List<CommandProposal> apply(List<CommandProposal> proposals, Result result, List<CommandSquadState> roster) {
        if (result.duties().isEmpty() && result.released().isEmpty()) return proposals;
        Map<Integer, CommandSquadState> squads = new HashMap<>();
        for (CommandSquadState squad : roster) squads.put(squad.squadId(), squad);
        List<CommandProposal> out = new ArrayList<>(proposals.size() + result.duties().size());
        for (CommandProposal proposal : proposals) {
            if (result.duties().containsKey(proposal.squadId()) || result.released().contains(proposal.squadId())) {
                continue;
            }
            out.add(proposal);
        }
        for (Map.Entry<Integer, Duty> entry : result.duties().entrySet()) {
            CommandSquadState squad = squads.get(entry.getKey());
            ObjectiveAssignment current = squad == null ? null
                    : squad.directive() != null ? squad.directive().assignment() : squad.assignment();
            Duty duty = entry.getValue();
            out.add(CommandProposal.assign(duty.assignment(), CommandAuthority.MISSION_COMMAND, duty.reason(),
                    Objects.equals(current, duty.assignment()) ? CommandStabilityBreak.NONE
                            : CommandStabilityBreak.CONTEXT_INVALIDATED));
        }
        for (int id : result.released()) {
            out.add(CommandProposal.claim(id, CommandAuthority.MISSION_COMMAND, RELEASED_REASON,
                    CommandStabilityBreak.CONTEXT_INVALIDATED));
        }
        return out;
    }

    /** The mission's manning offers less the group's squads, with a roving offer for each squad let go. */
    static Map<Integer, MissionGarrisons.Offer> offers(Map<Integer, MissionGarrisons.Offer> offers, Result result) {
        if (result.duties().isEmpty() && result.released().isEmpty()) return offers;
        Map<Integer, MissionGarrisons.Offer> out = new TreeMap<>(offers);
        out.keySet().removeAll(result.duties().keySet());
        for (int id : result.released()) out.put(id, MissionGarrisons.Offer.reserve());
        return out;
    }

    /**
     * A squad's body for the shared planner. It is "in contact" only while it is in a fight — shot at lately or firing
     * itself — not merely aware of one: on a mission's small map nearly every squad believes in a hostile it could act on
     * as soon as a sentry has seen the attack, and that alone would leave nobody to recruit (`MissionGarrisons` reads the
     * same distinction for staging).
     */
    private static Body body(CommandSquadState squad) {
        return new Body(squad.squadId(), squad.anchorCellX(), squad.anchorCellY(), squad.aliveMembers(),
                squad.originalMembers() > 0 ? squad.originalMembers() : squad.aliveMembers(), squad.machine(),
                squad.demolition(), fighting(squad), squad.assignment());
    }

    /** Whether a squad is in a fight rather than merely aware of one. */
    static boolean fighting(CommandSquadState squad) {
        return squad.underFireRecently() || squad.coolingDownMembers() > 0;
    }
}
