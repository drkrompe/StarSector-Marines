package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.SquadDirective;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.TrackState;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.command.reinforcement.DeliveryDeployment;
import com.dillon.starsectormarines.battle.command.reinforcement.DeliveryDeploymentPolicy;
import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementRequest;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntBinaryOperator;

/**
 * Defender-side Conquest command: faction-honest first contact mobilizes a
 * bounded patrol reserve.
 *
 * <p>How much of that reserve moves is a share of the pool rather than a fixed
 * count. The original bound — one squad held back, two responders per
 * threatened track — was written for a starting force of a few patrols and read
 * as caution there. At a pool of sixty-two it was not caution but abdication:
 * a live battle showed three threatened tracks drawing six squads while
 * fifty-six sat on a home-track hold and the base was taken compound by
 * compound. A tenth of a defence committed is indistinguishable from no
 * commander at all.
 *
 * <p>So the reserve is {@link #RESERVE_SHARE} of the mobile pool, floored at
 * {@link #MIN_MOBILE_RESERVE}, and each threatened track may draw its own
 * fraction of what remains — its share of the believed contacts across every
 * active threat, floored at {@link #MAX_RESPONDERS_PER_TRACK} so a three-patrol
 * pool answers exactly as it always did. Contacts rather than the diffused
 * pressure field: a contact is a counted report, while pressure is sampled at
 * influence-block centres and measures the block grid as much as the front.
 *
 * <p>Conquest evidence, the scaled response against the fixed bound:
 *
 * <table>
 *   <caption>Conquest matrix, scaled response vs. fixed bound</caption>
 *   <tr><th>fixture</th><th>captures</th><th>held at end</th>
 *       <th>marines lost</th><th>defenders lost</th><th>ticks</th>
 *       <th>result</th><th>defender reserve (squad-ticks)</th></tr>
 *   <tr><td>reinforced-south scaled</td><td>7</td><td>4</td><td>241</td>
 *       <td>348</td><td>18000</td><td>TIMEOUT</td><td>57599</td></tr>
 *   <tr><td>reinforced-south fixed</td><td>12</td><td>11</td><td>216</td>
 *       <td>390</td><td>18000</td><td>TIMEOUT</td><td>267524</td></tr>
 *   <tr><td>full-strength-west scaled</td><td>2</td><td>0</td><td>420</td>
 *       <td>470</td><td>15599</td><td>TERMINAL DEFENDER</td><td>188386</td></tr>
 *   <tr><td>full-strength-west fixed</td><td>3</td><td>3</td><td>426</td>
 *       <td>311</td><td>16445</td><td>TERMINAL DEFENDER</td><td>1078704</td></tr>
 * </table>
 *
 * <p>Captures fall and ground held falls, which is the point rather than a
 * regression: the attacker was taking compounds against a defence that was
 * standing still. The scaled response stands because the marines still take
 * compounds on both fixtures and both runs still end the way they did, while the defender's
 * published {@code DEFEND_TRACK} share rises from 7% to 14% and from 3% to 19%
 * of its squad-pulses and its reserve time falls by roughly four fifths.
 */
public final class ConquestDefenderCommand implements ConquestFrontCommand,
        AutonomousMissionCommand<ConquestCommandFrame, ConquestFrontSnapshot>,
        DeliveryDeploymentPolicy {

    static final int MIN_MOBILE_RESERVE = 1;
    static final int MAX_RESPONDERS_PER_TRACK = 2;

    /**
     * Fraction of the mobile pool held back once the pool is large enough for
     * a fraction to mean anything. A one-squad reserve is the same reserve at
     * three patrols and at sixty; expressed as a share it stays a reserve as
     * the garrison grows instead of becoming a rounding error.
     */
    static final float RESERVE_SHARE = 0.25f;

    static final int COARSE_BAND_CELLS = 16;
    static final int RALLY_REAR_OFFSET_CELLS = 6;
    static final int CONVOY_REAR_STANDOFF_CELLS = 12;
    static final int CONVOY_DEPLOYMENT_BAND_CELLS = 4;
    private static final int RALLY_SNAP_RADIUS = 12;

    /**
     * How long a track stays threatened after its last believed contact, in ticks: 15 s. Belief of an enemy lapses
     * whenever nobody happens to be looking, and a threat that lapsed with it recalled every responder to reserve on the
     * next pulse and sent it again on the one after, so squads crawled a few cells per order and never arrived. UNTUNED.
     */
    static final int THREAT_MEMORY_TICKS = 450;

    private final ConquestTrackLayout trackLayout;
    private final Set<Integer> initialMobileSquads = new TreeSet<>();
    /**
     * Squads handed to this commander after setup (a claim or a delivery in its name) and seen carrying its directive.
     * They stay in the pool once it releases their order, as the starting force does: a release removes the directive,
     * and a pool known only by its directive lost every such squad, lance or patrol, the first time its track response
     * ended, for the rest of the battle.
     */
    private final Set<Integer> handedSquads = new TreeSet<>();
    private final Map<Integer, Integer> homeTracks = new HashMap<>();
    /** Per track, the tick of its last believed contact and the depth and front it reached then; for threat memory. */
    private int[] threatSeenTick = new int[0];
    private int[] threatSeenForward = new int[0];
    private float[] threatSeenFront = new float[0];
    private volatile ConquestFrontSnapshot frontSnapshot;
    private final GarrisonDirector garrisons = new GarrisonDirector();
    /** Which free lance stands guard by which manned place (`garrisonables.md`, machine guard). */
    private final MachineGuardDirector machineGuards = new MachineGuardDirector();
    /** Guards the last plan attached and stood down, ordered in {@link #afterCommit}. */
    private List<MachineGuardDirector.Attach> guardsToOrder = List.of();
    private List<Integer> guardsToStandDown = List.of();
    /** Alarms on this side's own forts: the pressure kept up while nobody can see the attack ({@link PlaceAlarms}). */
    private final PlaceAlarms alarms = new PlaceAlarms();
    /** The counter-attack on a place this side has lost or is losing, with the reserve held for it ({@link PlaceCounterattack}). */
    private final PlaceCounterattack counter = new PlaceCounterattack();
    /** Which of this side's compounds it has lost and not yet won back. */
    private final CompoundPlaces compoundPlaces = new CompoundPlaces();
    /** A rallied squad whose current track post lies within this many cells (Chebyshev) of its area's new staging cell keeps it. */
    private static final int RALLY_KEEP_CELLS = 8;
    /** Plans between attempts to stage for an area once no patrol could be found for it. */
    private static final int STAGE_RETRY_PLANS = 8;
    /** Most patrols newly sent to wait in one plan, across all areas. UNTUNED. */
    private static final int MAX_STAGE_PER_PLAN = 3;
    /** Consecutive plans (2.5 s each) a staged patrol's area must show no believed pressure before it is given back. UNTUNED. */
    private static final int STAGE_COOL_PLANS = 20;
    /** Patrols tried (nearest first) for one area's waiting post before giving up until the retry. */
    private static final int STAGE_CANDIDATES_TRIED = 3;
    /** Cells around a general staging cell that an area defence covers. */
    private static final int STAGE_RADIUS_CELLS = 3;
    /** Rings searched for a walkable staging cell around the desired one. */
    private static final int STAGE_SNAP_RADIUS = 8;
    /** The mobile-pool patrols waiting for pressure: squad id to its post. Never a born garrison; each is sent like any free patrol. */
    private final Map<Integer, StagePost> stagers = new HashMap<>();
    private final Map<Long, Integer> stageRetryAt = new HashMap<>();
    private int planCount;
    private int[] contactScratch = new int[128];
    private int[] flowBlocks = new int[64];
    private float[] flowPressure = new float[64];
    private int[] flowStamp = new int[0];
    private int flowGeneration;
    /** The last plan's flow line, appended to its published summaries. */
    private String flowNote = "";
    /** This plan's per-area reservations (see {@link AreaReservations}); empty without an influence picture. */
    private AreaReservations reservations = new AreaReservations(0);
    /** This plan's pressure areas, found before the track rally chooses its destinations; {@code null} without an influence picture. */
    private AreaPlan areaPlan;
    /** For each track, the area its rally maps onto this plan, or -1. */
    private int[] rallyAreaOfTrack = new int[0];
    /** Track rallies redirected to an area's staging cell this plan. */
    private int rallyRedirects;

    /**
     * The pressure areas of a plan and what each offers: whether it has anything to man (a garrisonable under believed
     * pressure), the fort works that would shelter it, and the cell squads wait on for it. {@code stage} is {@code null}
     * for an area not worth answering and, when only worthwhile areas are staged, for one with nothing to man.
     */
    private static final class AreaPlan {
        final List<GarrisonFlow.Area> areas;
        final boolean[] workful;
        final int[] warmFort;
        final DefenceStructureFact[] site;
        final int[][] stage;

        AreaPlan(List<GarrisonFlow.Area> areas) {
            this.areas = areas;
            int n = areas.size();
            workful = new boolean[n];
            warmFort = new int[n];
            Arrays.fill(warmFort, -1);
            site = new DefenceStructureFact[n];
            stage = new int[n][];
        }

        int size() {
            return areas.size();
        }
    }

    /**
     * A waiting patrol's order, the centre of the area it was posted for (the post can stand well behind it, at a fort's
     * works), and the consecutive plans that area has been quiet.
     */
    private static final class StagePost {
        final ObjectiveAssignment order;
        final int areaX;
        final int areaY;
        int quietPlans;

        StagePost(ObjectiveAssignment order, int areaX, int areaY) {
            this.order = order;
            this.areaX = areaX;
            this.areaY = areaY;
        }
    }

    private static final class Threat {
        final int track;
        int contacts;
        int deepestForward = -1;
        float knownHostileFront = -1f;
        float friendlyPressure;
        float hostilePressure;
        /** No contact now, but one recently enough that the threat stands ({@link #THREAT_MEMORY_TICKS}). */
        boolean remembered;
        /** A fort of this side on the track is alarmed. */
        boolean alarmed;

        Threat(int track) { this.track = track; }
        boolean active() { return contacts > 0 || remembered || alarmed; }
    }

    /** Mutable working copy; never exposes or mutates a live squad. */
    private static final class PlanningSquad {
        final int id;
        final int aliveMembers;
        final int originalMembers;
        final float centroidX;
        final float centroidY;
        final int anchorCellX;
        final int anchorCellY;
        final int currentZoneId;
        final UnitRole role;
        final boolean localContact;
        final String executionSuspension;
        final int activePathMembers;
        final boolean underFireRecently;
        final boolean moraleBroken;
        final String currentGoal;
        final String currentAction;
        final int movingMembers;
        final int coveredFromPrimaryMembers;
        final int primaryEngageableMembers;
        final int primaryEngageableFireTeams;
        final String contactPosture;
        final String contactDoctrine;
        final String contactInitiative;
        final int coolingDownMembers;
        final int[] memberZoneIds;
        final boolean demolition;
        final boolean crew;
        final boolean machine;
        final CommandDirective originalDirective;
        ObjectiveAssignment assignedObjective;

        PlanningSquad(CommandSquadState state) {
            id = state.squadId();
            aliveMembers = state.aliveMembers();
            originalMembers = state.originalMembers();
            centroidX = state.centroidX();
            centroidY = state.centroidY();
            anchorCellX = state.anchorCellX();
            anchorCellY = state.anchorCellY();
            currentZoneId = state.currentZoneId();
            role = state.role();
            localContact = state.localContact();
            executionSuspension = state.executionSuspension();
            activePathMembers = state.activePathMembers();
            underFireRecently = state.underFireRecently();
            moraleBroken = state.moraleBroken();
            currentGoal = state.currentGoal();
            currentAction = state.currentAction();
            movingMembers = state.movingMembers();
            coveredFromPrimaryMembers = state.coveredFromPrimaryMembers();
            primaryEngageableMembers = state.primaryEngageableMembers();
            primaryEngageableFireTeams = state.primaryEngageableFireTeams();
            contactPosture = state.contactPosture();
            contactDoctrine = state.contactDoctrine();
            contactInitiative = state.contactInitiative();
            coolingDownMembers = state.coolingDownMembers();
            memberZoneIds = state.memberZoneIds();
            demolition = state.demolition();
            crew = state.crew();
            machine = state.machine();
            originalDirective = state.directive();
            assignedObjective = state.assignment();
        }
    }

    /** A rally destination; {@code area} marks one taken from a pressure area's staging cell rather than the track's band. */
    private record Rally(int x, int y, boolean area) {
        Rally(int x, int y) {
            this(x, y, false);
        }
    }
    private record CandidateChoice(PlanningSquad squad, Rally rally) { }

    private static final int[][] RALLY_ALTERNATIVES = {
            {0, 0}, {-4, 0}, {4, 0}, {0, -4}, {0, 4},
            {-8, 0}, {8, 0}, {0, -8}, {0, 8}
    };

    public ConquestDefenderCommand(ConquestTrackLayout trackLayout) {
        this(trackLayout, ConquestDefenderStartingForce.empty());
    }

    public ConquestDefenderCommand(ConquestTrackLayout trackLayout,
                                   ConquestDefenderStartingForce startingForce) {
        this.trackLayout = trackLayout;
        initialMobileSquads.addAll(startingForce.mobileSquadIds());
        homeTracks.putAll(startingForce.homeTracks());
        this.frontSnapshot = ConquestFrontSnapshot.empty(
                Faction.DEFENDER, trackLayout.axis());
    }

    @Override public Faction faction() { return Faction.DEFENDER; }
    @Override public ConquestFrontSnapshot frontSnapshot() { return frontSnapshot; }

    @Override
    public String strategyId() {
        return "conquest-defender";
    }

    @Override
    public CommandPlan<ConquestFrontSnapshot> plan(ConquestCommandFrame frame) {
        structures = frame.facts().structures();
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence != null) {
            alarms.update(frame.tick(), faction(), structures, new PlaceAlarms.Reading() {
                @Override public float hostileAt(int bx, int by) { return influence.hostileAt(bx, by); }
                @Override public float lossesAt(int bx, int by) { return influence.lossesAt(bx, by); }
            }, influence.blockSize(), influence.width(), influence.height());
        }
        placeCounterattackGround(frame);
        Threat[] threats = buildThreats(influence, frame.tick());
        Map<Integer, Rally> rallies = new HashMap<>();
        for (Threat threat : threats) {
            if (threat.active()) rallies.put(threat.track,
                    rallyFor(threat, frame.topology()));
        }

        GarrisonableRegistry places = frame.facts().garrisonables();
        compoundsThisPlan = frame.facts().compounds();
        boolean reactive = reactiveManning(places, influence);
        List<GarrisonDirector.Garrison> held = new ArrayList<>();
        Map<Integer, PlanningSquad> heldSquads = new TreeMap<>();
        Set<Integer> ownGarrisoned = new TreeSet<>();
        List<PlanningSquad> candidates = new ArrayList<>();
        Map<Integer, PlanningSquad> staged = new TreeMap<>();
        // The counter-attack's squads (its reserve, its muster, its launched group): out of every other assignment.
        Map<Integer, PlanningSquad> countering = new TreeMap<>();
        boolean counterOn = PlaceCounterattack.isEnabled();
        boolean flowOn = influence != null;
        flowNote = "";
        planCount++;
        areaPlan = null;
        reservations = new AreaReservations(0);
        rallyAreaOfTrack = new int[trackLayout.trackCount()];
        Arrays.fill(rallyAreaOfTrack, -1);
        rallyRedirects = 0;
        Map<Integer, PlanningSquad> allSquads = new TreeMap<>();
        Map<Integer, SquadDirective> directives = new TreeMap<>();
        Map<Integer, PlanningSquad> guarding = new TreeMap<>();
        List<PlanningSquad> contactMachines = new ArrayList<>();
        for (CommandSquadState state : frame.squads()) {
            PlanningSquad squad = new PlanningSquad(state);
            allSquads.put(squad.id, squad);
            if (squad.aliveMembers <= 0) continue;
            int home = homeTrack(squad);
            // A lance on guard is this commander's even when its last plan proposed nothing to it: a guard order is a
            // mech's, and a lance sent with no squad order of its own otherwise fell out of the pool a pulse later.
            if (!isCommandPoolSquad(squad) && !(squad.machine && machineGuards.holds(squad.id))) {
                AssignmentReason reason = squad.role == UnitRole.GARRISON
                        ? AssignmentReason.DEFENDER_GARRISON_HOLD
                        : AssignmentReason.DEFENDER_EXTERNAL_ASSIGNMENT_PRESERVED;
                directives.put(squad.id, directive(squad, home, home, reason));
                if (reactive && squad.role == UnitRole.GARRISON) {
                    // A building its own garrison still stands in is manned: the pool reinforces it only under heavy
                    // pressure, and re-garrisons it once that garrison is gone.
                    int place = places.idAt((int) squad.centroidX, (int) squad.centroidY);
                    if (place >= 0 && places.isEnvelope(place)) ownGarrisoned.add(place);
                }
                continue;
            }
            if (counterOn && counter.claims(squad.id) && !hasHigherAuthority(squad)) {
                stagers.remove(squad.id);
                countering.put(squad.id, squad);
                continue;
            }
            if (reactive && !hasHigherAuthority(squad)) {
                GarrisonDirector.Garrison garrison = garrisonOf(squad, places);
                if (garrison != null) {
                    stagers.remove(squad.id);
                    held.add(garrison);
                    heldSquads.put(squad.id, squad);
                    continue;
                }
            }
            if (machineGuards.holds(squad.id) && !hasHigherAuthority(squad)) {
                // A lance standing guard by a manned place answers to the guard, not to this pulse's response.
                guarding.put(squad.id, squad);
                continue;
            }
            StagePost post = flowOn && !hasHigherAuthority(squad) ? stagers.get(squad.id) : null;
            if (post != null) {
                if (stageStands(squad, post)) {
                    directives.put(squad.id, directive(squad, home, home, squad.localContact
                            ? AssignmentReason.DEFENDER_LOCAL_CONTACT : AssignmentReason.DEFENDER_GARRISONABLE_STAGE));
                    if (!squad.localContact) staged.put(squad.id, squad);
                    continue;
                }
                stagers.remove(squad.id);
            }
            if (keepsInside(squad, influence)) {
                directives.put(squad.id, directive(squad, home, home, AssignmentReason.DEFENDER_GARRISONABLE_FALL_BACK));
                continue;
            }
            if (counterOn && leftByCounterattack(squad)) {
                // The counter-attack has let it go (a stand-down, a retake, a failure): its staging post or retake
                // order was the counter-attack's, not an external task, so it is the pool's again.
                squad.assignedObjective = null;
            } else {
                counterLeft.remove(squad.id);
            }
            if (squad.localContact) {
                if (squad.machine) contactMachines.add(squad);
                clearMissionRally(squad);
                directives.put(squad.id, directive(squad, home, home,
                        AssignmentReason.DEFENDER_LOCAL_CONTACT));
                continue;
            }
            if (squad.assignedObjective != null
                    && squad.assignedObjective.kind() != AssignmentKind.DEFEND_TRACK
                    && !DefenceStructureTactics.isTask(squad.assignedObjective, structures)) {
                AssignmentReason reason = ownsReliefObjective(squad)
                        ? AssignmentReason.DEFENDER_RELIEF_OBJECTIVE
                        : AssignmentReason.DEFENDER_EXTERNAL_ASSIGNMENT_PRESERVED;
                directives.put(squad.id, directive(squad, home, home, reason));
                continue;
            }
            if (hasHigherAuthority(squad)) {
                directives.put(squad.id, directive(squad, home, home,
                        AssignmentReason.DEFENDER_EXTERNAL_ASSIGNMENT_PRESERVED));
                continue;
            }
            candidates.add(squad);
        }

        Set<Integer> justReleased = new HashSet<>();
        if (reactive) {
            standOrRelease(places, influence, held, heldSquads, candidates, directives, justReleased,
                    frame.topology(), frame.perspective());
            garrisons.heldByOwnGarrison(ownGarrisoned);
        }

        if (flowOn) {
            planAreas(influence, reactive ? places : null, frame.topology(), frame.perspective(), threats, rallies, held, allSquads);
        }
        for (Map.Entry<Integer, Rally> entry : rallies.entrySet()) {
            entry.setValue(steadyRallies.steady(entry.getKey(), entry.getValue(), frame.tick()));
        }

        int mobileReserve = counterOn ? counterReserve(candidates.size()) : mobileReserveFor(candidates.size());
        int responseBudget = candidates.size() - mobileReserve;
        Set<Integer> selected = new HashSet<>();
        List<Threat> activeThreats = Arrays.stream(threats)
                .filter(Threat::active)
                .sorted(Comparator.comparingInt((Threat t) -> -t.contacts)
                        .thenComparingInt(t -> t.track))
                .toList();
        int[] responderCaps = responderCaps(activeThreats, responseBudget);

        // First pass spreads the response across distinct threatened tracks.
        for (Threat threat : activeThreats) {
            if (selected.size() >= responseBudget) break;
            CandidateChoice choice = chooseCandidate(candidates, selected,
                    threat.track, rallies.get(threat.track), frame.topology());
            if (choice != null) assignResponse(choice, threat.track,
                    directives, selected);
        }
        // The counter-attack masses after every threatened track has its picket and before any track has a second.
        List<String> counterNotes = List.of();
        if (counterOn) {
            counterNotes = planCounterattack(frame, influence, candidates, countering, selected, justReleased, directives);
            mobileReserve = counterReserve(candidates.size());
            responseBudget = candidates.size() - mobileReserve;
        }
        // Concentration follows, up to each track's own share of the budget.
        for (Threat threat : activeThreats) {
            while (selected.size() < responseBudget
                    && respondersFor(threat.track, directives)
                    < responderCaps[threat.track]) {
                CandidateChoice choice = chooseCandidate(candidates, selected,
                        threat.track, rallies.get(threat.track), frame.topology());
                if (choice == null) break;
                assignResponse(choice, threat.track, directives, selected);
            }
        }

        List<String> structureNotes = assignDefenceStructureTasks(frame, candidates, selected, directives);
        // Manning a pressed place outranks staging: it takes the free or waiting squads that can arrive in time, and
        // flow then posts what is left (counting the squads just sent as manned) behind whatever is still unanswered.
        if (reactive) manGarrisonables(places, frame.topology(), candidates, staged, allSquads, selected, justReleased, directives);
        planMachineGuards(reactive ? places : null, ownGarrisoned, candidates, contactMachines, selected, guarding,
                allSquads, directives);
        if (flowOn) {
            flowToPressure(influence, reactive ? places : null, frame.topology(), frame.perspective(), held,
                    candidates, staged, allSquads, selected, justReleased, directives);
        }

        for (PlanningSquad squad : candidates) {
            if (selected.contains(squad.id)) continue;
            clearMissionRally(squad);
            int home = homeTrack(squad);
            directives.put(squad.id, directive(squad, home, home,
                    AssignmentReason.DEFENDER_RESERVE_HOLD));
        }

        reconcileOrderlessDirectives(allSquads, directives);

        Phase phase = activeThreats.isEmpty() ? Phase.LANE_ADVANCE : Phase.FRONT_ADJUST;
        ConquestFrontSnapshot detail = buildFrontSnapshot(frame, influence,
                threats, responderCaps, directives, allSquads, phase);
        List<CommandProposal> proposals = buildProposals(
                frame, allSquads, directives, threats);
        return new CommandPlan<>(faction(), strategyId(), phase.name(), frame.tick(),
                influence != null ? influence.updatedTick() : -1,
                candidates.size() + stagedOutside(candidates) + countering.size(),
                candidates.size() - selected.size() + stagers.size() + counter.heldInReserve(),
                summaries(activeThreats.size(), responseBudget, mobileReserve, structureNotes, counterNotes, flowNote,
                        alarms.describe()),
                proposals, detail);
    }

    /**
     * The machine guard's commander half: stands each guarding lance by its place or stands it down, then attaches a
     * free lance to a manned, pressed place nobody guards, by {@link MachineGuardDirector}'s laws. A lance is free when it
     * is in the pool and this pulse gave it nothing (no track response, no structure task); a lance in local contact is
     * offered too, for a place near its fight. The orders are given in {@link #afterCommit}.
     */
    private void planMachineGuards(GarrisonableRegistry places, Set<Integer> ownGarrisoned,
                                   List<PlanningSquad> candidates,
                                   List<PlanningSquad> contactMachines, Set<Integer> selected,
                                   Map<Integer, PlanningSquad> guarding, Map<Integer, PlanningSquad> allSquads,
                                   Map<Integer, SquadDirective> directives) {
        List<MachineGuardDirector.Place> manned = places == null ? List.of()
                : mannedPlaces(places, allSquads, directives, ownGarrisoned, garrisons.hotPlaces());
        IntBinaryOperator placeAt = places == null ? (x, y) -> -1 : places::idAt;
        Map<Integer, MachineGuardDirector.Machine> lances = new TreeMap<>();
        for (PlanningSquad squad : guarding.values()) lances.put(squad.id, machineOf(squad));
        List<Integer> down = machineGuards.review(lances, manned, placeAt);
        for (int id : down) {
            // A lance standing down from a guard is a reserve again; one taken by other work keeps that work's directive.
            PlanningSquad squad = guarding.remove(id);
            if (squad == null) continue;
            int home = homeTrack(squad);
            directives.put(id, directive(squad, home, home, AssignmentReason.DEFENDER_RESERVE_HOLD));
        }
        for (PlanningSquad squad : guarding.values()) {
            int home = homeTrack(squad);
            directives.put(squad.id, directive(squad, home, home, AssignmentReason.DEFENDER_MACHINE_GUARD));
        }
        List<MachineGuardDirector.Machine> free = new ArrayList<>();
        for (PlanningSquad squad : candidates) {
            if (squad.machine && !selected.contains(squad.id)) free.add(machineOf(squad));
        }
        for (PlanningSquad squad : contactMachines) {
            free.add(new MachineGuardDirector.Machine(squad.id, squad.anchorCellX, squad.anchorCellY,
                    squad.aliveMembers, squad.originalMembers, true));
        }
        int machineSquads = 0;
        for (PlanningSquad squad : allSquads.values()) if (squad.machine && squad.aliveMembers > 0) machineSquads++;
        List<MachineGuardDirector.Attach> sent = machineGuards.attach(free, manned, machineSquads, placeAt);
        for (MachineGuardDirector.Attach attach : sent) {
            PlanningSquad squad = allSquads.get(attach.squadId());
            int home = homeTrack(squad);
            directives.put(squad.id, directive(squad, home, home, AssignmentReason.DEFENDER_MACHINE_GUARD));
            selected.add(squad.id);
        }
        guardsToOrder = sent;
        guardsToStandDown = down;
    }

    /**
     * The places this side mans after this pulse's manning: every stretch or building envelope a squad is ordered to
     * man and is not falling back from, and every building its own garrison still stands in, the pressed ones first in
     * the director's own order (most pressed first), then the rest by id.
     */
    static List<MachineGuardDirector.Place> mannedPlaces(GarrisonableRegistry places, Map<Integer, PlanningSquad> squads,
                                                         Map<Integer, SquadDirective> directives,
                                                         Set<Integer> ownGarrisoned, int[] hotPlaces) {
        Set<Integer> mannedIds = new TreeSet<>(ownGarrisoned);
        for (PlanningSquad squad : squads.values()) {
            if (squad.aliveMembers <= 0 || squad.machine) continue;
            int place = MachineGuardDirector.placeManned(squad.assignedObjective, places);
            if (place < 0) continue;
            // A squad driven off a stretch is leaving it; one driven off an apron into its building mans the building.
            SquadDirective directive = directives.get(squad.id);
            if (directive != null && directive.reason() == AssignmentReason.DEFENDER_GARRISONABLE_FALL_BACK
                    && !places.isEnvelope(place)) continue;
            mannedIds.add(place);
        }
        return MachineGuardDirector.manned(mannedIds, hotPlaces, places);
    }

    private static MachineGuardDirector.Machine machineOf(PlanningSquad squad) {
        return new MachineGuardDirector.Machine(squad.id, squad.anchorCellX, squad.anchorCellY,
                squad.aliveMembers, squad.originalMembers);
    }

    /** Gives the machine guards the plan attached, and stands down the ones it ended. */
    @Override
    public void afterCommit(CommandEffects effects) {
        for (int squadId : sortiesToHandOff) {
            effects.takeOverSquad(squadId, SETUP_GARRISON_ISSUER, strategyId(), "garrison sorties into a counter-attack");
        }
        sortiesToHandOff = List.of();
        for (int squadId : guardsToStandDown) effects.standDownGuard(squadId);
        for (MachineGuardDirector.Attach attach : guardsToOrder) {
            if (effects.guardPlace(faction(), attach.squadId(), attach.cellX(), attach.cellY()) == 0) {
                machineGuards.refused(attach.squadId(), attach.garrisonable());
            }
        }
        guardsToOrder = List.of();
        guardsToStandDown = List.of();
    }

    /** Lances standing guard by a manned place; for evidence. */
    int machineGuardCount() {
        return machineGuards.count();
    }

    private static List<String> summaries(Object threats, Object budget, Object reserve, List<String> structureNotes,
                                          List<String> counterNotes, String flowNote, String alarmNote) {
        List<String> out = new ArrayList<>();
        if (!flowNote.isEmpty()) out.add(flowNote);
        if (!alarmNote.isEmpty()) out.add("place alarms: " + alarmNote);
        out.add("active threat tracks=" + threats);
        out.add("response budget=" + budget);
        out.add("held reserve=" + reserve);
        out.addAll(structureNotes);
        out.addAll(counterNotes);
        return out;
    }

    /**
     * How many of the mobile pool are kept out of the response.
     *
     * <p>A share rather than a count, because the pool is the whole starting
     * garrison and its size varies by an order of magnitude between targets.
     * The floor is what keeps a three-patrol pool behaving exactly as it did:
     * two respond and one waits.
     */
    private int mobileReserveFor(int poolSize) {
        if (poolSize < 2) return 0;
        return Math.max(MIN_MOBILE_RESERVE, Math.round(poolSize * RESERVE_SHARE));
    }

    /**
     * The most responders each track may draw, indexed by track, {@code -1}
     * where no threat is believed.
     *
     * <p>A track's share is its fraction of the believed contacts across every
     * active threat. Contacts are counted reports rather than the diffused
     * pressure field, which is sampled at influence-block centres and so
     * measures the block grid as much as the front. The fixed cap survives as
     * the floor: two responders is the smallest answer a threat gets, however
     * small its share of a large budget.
     */
    private int[] responderCaps(List<Threat> activeThreats, int responseBudget) {
        int[] caps = new int[trackLayout.trackCount()];
        Arrays.fill(caps, -1);
        int totalContacts = 0;
        for (Threat threat : activeThreats) totalContacts += threat.contacts;
        for (Threat threat : activeThreats) {
            if (totalContacts <= 0) {
                caps[threat.track] = MAX_RESPONDERS_PER_TRACK;
                continue;
            }
            int share = (int) Math.ceil((double) responseBudget
                    * threat.contacts / totalContacts);
            caps[threat.track] = Math.max(MAX_RESPONDERS_PER_TRACK, share);
        }
        return caps;
    }

    @Override
    public CommanderSnapshot<ConquestFrontSnapshot> reconcile(
            CommanderSnapshot<ConquestFrontSnapshot> snapshot) {
        return snapshot.withDetail(snapshot.detail().reconcileStableDirectives(
                snapshot, frontSnapshot, strategyId()));
    }

    @Override
    public void publish(CommanderSnapshot<ConquestFrontSnapshot> snapshot) {
        frontSnapshot = snapshot.detail();
    }

    private Threat[] buildThreats(CommanderInfluenceSnapshot influence, int tick) {
        Threat[] threats = new Threat[trackLayout.trackCount()];
        for (int i = 0; i < threats.length; i++) threats[i] = new Threat(i);
        if (influence == null) return threats;
        if (threatSeenTick.length != threats.length) {
            threatSeenTick = new int[threats.length];
            Arrays.fill(threatSeenTick, Integer.MIN_VALUE);
            threatSeenForward = new int[threats.length];
            threatSeenFront = new float[threats.length];
        }

        for (CommanderContact contact : influence.contacts()) {
            int track = trackLayout.trackForCell(contact.cellX(), contact.cellY());
            if (track < 0 || track >= threats.length) continue;
            // A turned fort's ground is the counter-attack's question: the track response does not rally into it.
            if (onTurnedGround(contact)) continue;
            Threat threat = threats[track];
            threat.contacts++;
            int forward = Math.round(trackLayout.forwardCoordinate(
                    contact.cellX(), contact.cellY()));
            threat.deepestForward = Math.max(threat.deepestForward, forward);
            threat.knownHostileFront = Math.max(threat.knownHostileFront,
                    trackLayout.assaultProgress(contact.cellX(), contact.cellY()));
        }
        for (Threat threat : threats) {
            int i = threat.track;
            if (threat.contacts > 0) {
                threatSeenTick[i] = tick;
                threatSeenForward[i] = threat.deepestForward;
                threatSeenFront[i] = threat.knownHostileFront;
            } else if (threatSeenTick[i] != Integer.MIN_VALUE && tick - threatSeenTick[i] <= THREAT_MEMORY_TICKS) {
                // Where it was last believed to be: the rally stays put rather than falling back to the track's rear.
                threat.remembered = true;
                threat.deepestForward = threatSeenForward[i];
                threat.knownHostileFront = threatSeenFront[i];
            }
        }
        // An alarmed fort threatens its own track, so the reserve answers an attack nobody can see into.
        for (DefenceStructureFact aspect : structures) {
            if (!aspect.ofFort() || aspect.originalHolder() != faction() || !alarms.alarmed(aspect.fortId())) continue;
            if (turnedFortIds.contains(aspect.fortId())) continue;
            int track = trackLayout.trackForCell(aspect.cellX(), aspect.cellY());
            if (track < 0 || track >= threats.length) continue;
            Threat threat = threats[track];
            threat.alarmed = true;
            threat.deepestForward = Math.max(threat.deepestForward,
                    Math.round(trackLayout.forwardCoordinate(aspect.cellX(), aspect.cellY())));
        }
        for (int by = 0; by < influence.height(); by++) {
            for (int bx = 0; bx < influence.width(); bx++) {
                int x = influence.blockWorldX(bx) + influence.blockWorldWidth(bx) / 2;
                int y = influence.blockWorldY(by) + influence.blockWorldHeight(by) / 2;
                int track = trackLayout.trackForCell(x, y);
                if (track < 0 || track >= threats.length) continue;
                threats[track].friendlyPressure += influence.friendlyAt(bx, by);
                threats[track].hostilePressure += placePressure(influence, bx, by);
            }
        }
        return threats;
    }

    private Rally rallyFor(Threat threat, CommandTopology topology) {
        int bandStart = Math.max(0, threat.deepestForward / COARSE_BAND_CELLS
                * COARSE_BAND_CELLS);
        int forward = Math.min(trackLayout.forwardExtent() - 1,
                bandStart + COARSE_BAND_CELLS + RALLY_REAR_OFFSET_CELLS);
        int lateral = trackLayout.lateralCenterCell(threat.track);
        int x = trackLayout.cellX(lateral, forward);
        int y = trackLayout.cellY(lateral, forward);
        return snapToTrackWalkable(x, y, threat.track, topology);
    }

    private Rally snapToTrackWalkable(int desiredX, int desiredY, int track,
                                      CommandTopology topology) {
        for (int radius = 0; radius <= RALLY_SNAP_RADIUS; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int dx = radius - Math.abs(dy);
                Rally left = validRally(desiredX - dx, desiredY + dy, track, topology);
                if (left != null) return left;
                if (dx != 0) {
                    Rally right = validRally(desiredX + dx, desiredY + dy, track, topology);
                    if (right != null) return right;
                }
            }
        }
        return new Rally(desiredX, desiredY);
    }

    private Rally validRally(int x, int y, int track, CommandTopology topology) {
        return topology.inBounds(x, y) && topology.isWalkable(x, y)
                && trackLayout.trackForCell(x, y) == track ? new Rally(x, y) : null;
    }

    /**
     * An area's staging cell moves with its pressure-weighted centre, which a few cells of contact movement shifts every
     * plan, where the band rally it replaces moves only a band at a time. A squad already marching to a post within
     * {@link #RALLY_KEEP_CELLS} of the new cell keeps that post, so the area's drift does not reset its route.
     */
    private Rally stickyRally(PlanningSquad squad, Rally coarse) {
        if (coarse == null || !coarse.area()) return coarse;
        ObjectiveAssignment current = squad.assignedObjective;
        if (current != null && current.kind() == AssignmentKind.DEFEND_TRACK
                && Math.max(Math.abs(current.targetCellX() - coarse.x()), Math.abs(current.targetCellY() - coarse.y()))
                <= RALLY_KEEP_CELLS) {
            return new Rally(current.targetCellX(), current.targetCellY(), true);
        }
        return coarse;
    }

    /**
     * The unification step: finds this plan's pressure areas before the track rally chooses, seeds the reservation table
     * with the squads already committed to them (those manning, those waiting), and maps each threatened track onto the
     * area its rally answers. A threatened track whose area offers something to man rallies to that area's staging cell
     * (or the fort's works) instead of the band point, so the squads it brings are already where manning will want them;
     * one whose area offers nothing keeps the band point, which is the old rally exactly.
     */
    private void planAreas(CommanderInfluenceSnapshot influence, GarrisonableRegistry places, CommandTopology topology,
                           Faction perspective, Threat[] threats, Map<Integer, Rally> rallies,
                           List<GarrisonDirector.Garrison> held, Map<Integer, PlanningSquad> allSquads) {
        dropDeadStagers(allSquads);
        AreaPlan plan = buildAreaPlan(influence, places, topology, perspective);
        areaPlan = plan;
        reservations = new AreaReservations(plan.size());
        if (places != null) {
            for (GarrisonDirector.Garrison garrison : held) {
                int a = GarrisonFlow.matchingArea(plan.areas, places.centreX(garrison.garrisonable()),
                        places.centreY(garrison.garrisonable()));
                reservations.commit(garrison.squadId(), a, AreaReservations.Kind.MANNED);
            }
        }
        for (Map.Entry<Integer, StagePost> entry : stagers.entrySet()) {
            StagePost post = entry.getValue();
            reservations.commit(entry.getKey(), GarrisonFlow.matchingArea(plan.areas, post.areaX, post.areaY),
                    AreaReservations.Kind.STAGED);
        }
        for (Threat threat : threats) {
            if (!threat.active()) continue;
            int a = GarrisonFlow.areaOfTrack(plan.areas, trackLayout::trackForCell, threat.track);
            rallyAreaOfTrack[threat.track] = a;
            if (a < 0 || !plan.workful[a] || plan.stage[a] == null) continue;
            int track = threat.track;
            int[] cell = GarrisonFlow.snap(plan.stage[a][0], plan.stage[a][1], STAGE_SNAP_RADIUS, (x, y) ->
                    topology.inBounds(x, y) && topology.isWalkable(x, y) && trackLayout.trackForCell(x, y) == track);
            // A staging cell off the track (a fort's works on its neighbour) leaves the old band point in force.
            if (cell != null) rallies.put(track, new Rally(cell[0], cell[1], true));
        }
    }

    /** Keeps each threatened track's rally from swinging back to the post it has just left. */
    private final SteadyRallies<Rally> steadyRallies = new SteadyRallies<>(new SteadyRallies.Cell<>() {
        @Override public int x(Rally post) { return post.x; }
        @Override public int y(Rally post) { return post.y; }
    });

    private CandidateChoice chooseCandidate(List<PlanningSquad> candidates,
                                            Set<Integer> selected,
                                            int effectiveTrack, Rally coarseRally,
                                            CommandTopology topology) {
        CandidateChoice best = null;
        long bestScore = Long.MAX_VALUE;
        for (PlanningSquad squad : candidates) {
            if (selected.contains(squad.id)) continue;
            // Standing to at a pressed work outranks a track rally; the structure planner keeps it there.
            if (DefenceStructureTactics.holdsPressedPost(squad.assignedObjective, faction(), structures)) continue;
            int home = homeTrack(squad);
            int trackDistance = Math.abs(home - effectiveTrack);
            if (trackDistance > 1) continue;
            Rally rally = reachableRally(squad, stickyRally(squad, coarseRally), effectiveTrack, topology);
            if (rally == null) continue;
            long dx = Math.round(squad.centroidX) - rally.x;
            long dy = Math.round(squad.centroidY) - rally.y;
            long score = trackDistance * 1_000_000L + dx * dx + dy * dy;
            ObjectiveAssignment current = squad.assignedObjective;
            if (current != null && current.kind() == AssignmentKind.DEFEND_TRACK
                    && current.targetCellX() == rally.x && current.targetCellY() == rally.y) {
                score -= 2_000_000L;
            }
            if (score < bestScore || (score == bestScore
                    && (best == null || squad.id < best.squad().id))) {
                best = new CandidateChoice(squad, rally);
                bestScore = score;
            }
        }
        return best;
    }

    private void assignResponse(CandidateChoice choice, int effectiveTrack,
                                Map<Integer, SquadDirective> directives,
                                Set<Integer> selected) {
        PlanningSquad squad = choice.squad();
        Rally rally = choice.rally();
        squad.assignedObjective = ObjectiveAssignment.defendTrack(
                squad.id, rally.x, rally.y);
        int home = homeTrack(squad);
        AssignmentReason reason = home == effectiveTrack
                ? AssignmentReason.DEFENDER_TRACK_RESPONSE
                : AssignmentReason.DEFENDER_ADJACENT_TRACK_RESPONSE;
        directives.put(squad.id, new SquadDirective(squad.id, home,
                effectiveTrack, reason, AssignmentKind.DEFEND_TRACK, -1,
                rally.x, rally.y));
        selected.add(squad.id);
        if (areaPlan != null) {
            int area = rallyAreaOfTrack[effectiveTrack];
            reservations.commit(squad.id, area, AreaReservations.Kind.RALLIED);
            if (rally.area()) rallyRedirects++;
        }
    }

    /** Resolves the coarse command point to a cell this squad can actually reach. */
    private Rally reachableRally(PlanningSquad squad, Rally desired, int track,
                                 CommandTopology topology) {
        int startX = squad.anchorCellX;
        int startY = squad.anchorCellY;
        if (!topology.inBounds(startX, startY) || !topology.isWalkable(startX, startY)) {
            return null;
        }
        for (int[] offset : RALLY_ALTERNATIVES) {
            Rally candidate = validRally(desired.x + offset[0],
                    desired.y + offset[1], track, topology);
            if (candidate != null && ((startX == candidate.x && startY == candidate.y)
                    || topology.reachable(startX, startY, candidate.x, candidate.y))) {
                return desired.area() ? new Rally(candidate.x, candidate.y, true) : candidate;
            }
        }
        return null;
    }

    private int respondersFor(int track, Map<Integer, SquadDirective> directives) {
        int count = 0;
        for (SquadDirective directive : directives.values()) {
            if (directive.effectiveTrack() == track
                    && (directive.reason() == AssignmentReason.DEFENDER_TRACK_RESPONSE
                    || directive.reason() == AssignmentReason.DEFENDER_ADJACENT_TRACK_RESPONSE)) {
                count++;
            }
        }
        return count;
    }

    /** The ground-defence structures this side knew of at the start of the current plan. */
    private List<DefenceStructureFact> structures = List.of();
    /** This plan's places for the counter-attack: its own forts and compounds ({@link PlaceCounterattack}). */
    private List<PlaceCounterattack.Place> counterPlaces = List.of();
    /**
     * Those of them turned, within reach of the keep and still the counter-attack's question, whose ground is the
     * counter-attack's alone. A place it has given up or stood down from as outmatched is the ordinary defence's again.
     */
    private List<PlaceCounterattack.Place> turnedGround = List.of();
    /** Every turned place within reach of the keep, claimed or not: its garrisonables are never manned. */
    private List<PlaceCounterattack.Place> lostGround = List.of();
    /** The forts among them that have turned. */
    private Set<Integer> turnedFortIds = Set.of();

    /** Builds this plan's places, the turned ground and the turned forts, or clears them with the counter-attack off. */
    private void placeCounterattackGround(ConquestCommandFrame frame) {
        if (!PlaceCounterattack.isEnabled()) {
            counterPlaces = List.of();
            turnedGround = List.of();
            lostGround = List.of();
            turnedFortIds = Set.of();
            return;
        }
        List<ConquestCommandFacts.Compound> compounds = frame.facts().compounds();
        List<PlaceCounterattack.Place> places = new ArrayList<>(FortPlaces.places(faction(), structures, alarms::alarmed));
        places.addAll(compoundPlaces.places(compounds,
                i -> compounds.get(i).node().kind != TacticalNode.Kind.COMMAND_POST));
        counterPlaces = places;
        List<PlaceCounterattack.Place> turned = new ArrayList<>(
                PlaceCounterattack.turnedGround(places, keepCell(frame), PlaceCounterattack.MAX_RETAKE_CELLS));
        List<PlaceCounterattack.Place> lost = List.copyOf(turned);
        turned.removeIf(place -> !counter.claimsGround(place.key()));
        lostGround = PlaceCounterattack.manLostGround ? List.copyOf(turned) : lost;
        turnedGround = turned;
        Set<Integer> forts = new TreeSet<>();
        for (Map.Entry<Integer, List<DefenceStructureFact>> fort : FortPlaces.ownForts(faction(), structures).entrySet()) {
            if (FortPlaces.status(faction(), fort.getValue()) != PlaceCounterattack.Status.HELD
                    && counter.claimsGround(FortPlaces.key(fort.getKey()))) forts.add(fort.getKey());
        }
        turnedFortIds = forts;
    }

    /**
     * Whether a believed contact stands on a turned place's ground. That ground is the counter-attack's question: the
     * track response does not rally into it, flow does not stage toward it and its garrisonables are not manned, so a
     * lost place is never fed squad by squad.
     */
    private boolean onTurnedGround(CommanderContact contact) {
        return onTurnedGround(contact.cellX(), contact.cellY());
    }

    private boolean onTurnedGround(int x, int y) {
        return !turnedGround.isEmpty() && PlaceCounterattack.onTurnedGround(turnedGround, counterPlaces, x, y);
    }

    private boolean hasHigherAuthority(PlanningSquad squad) {
        return squad.originalDirective != null
                && squad.originalDirective.authority().priority()
                > CommandAuthority.MISSION_COMMAND.priority();
    }

    /**
     * Squads the counter-attack has let go, with the order it last gave each. Until a squad's directive no longer holds
     * that order it is still the counter-attack's leftover, not an external task: without this a squad released from a
     * staging post kept it, read as an external assignment every pulse after, and was lost to the pool for the battle.
     */
    private final Map<Integer, ObjectiveAssignment> counterLeft = new TreeMap<>();
    /** The issuer of the authored Conquest garrisons ({@code BattleSetup}); a sortie takes a squad over from it. */
    static final String SETUP_GARRISON_ISSUER = "conquest-setup-garrison";
    /** A garrison with a believed hostile mover this close is not quiet and does not sortie. UNTUNED. */
    static final int SORTIE_QUIET_CELLS = 40;
    /** A garrison further than this from the place does not sortie to it. UNTUNED. */
    static final int SORTIE_REACH_CELLS = 120;
    /** A garrison belongs to the compound whose capture cell is nearest and at most this far. */
    static final int SORTIE_COMPOUND_CELLS = 30;
    /** Garrisons the last plan's counter-attack recruited, handed over in {@link #afterCommit}. */
    private List<Integer> sortiesToHandOff = List.of();

    /** Whether a squad still holds an order the counter-attack gave it, though the counter-attack no longer claims it. */
    private boolean leftByCounterattack(PlanningSquad squad) {
        ObjectiveAssignment left = counterLeft.get(squad.id);
        return left != null && squad.assignedObjective != null && !counter.claims(squad.id)
                && sameTask(left, squad.assignedObjective);
    }

    private static boolean sameTask(ObjectiveAssignment a, ObjectiveAssignment b) {
        return a.kind() == b.kind() && a.targetCellX() == b.targetCellX() && a.targetCellY() == b.targetCellY()
                && a.targetZoneId() == b.targetZoneId();
    }

    private boolean isCommandPoolSquad(PlanningSquad squad) {
        if (initialMobileSquads.contains(squad.id)) return true;
        if (squad.originalDirective == null) return handedSquads.contains(squad.id);
        boolean ours = squad.originalDirective.authority() == CommandAuthority.MISSION_COMMAND
                && strategyId().equals(squad.originalDirective.issuer());
        if (ours) handedSquads.add(squad.id);
        return ours;
    }

    private boolean ownsReliefObjective(PlanningSquad squad) {
        return squad.assignedObjective != null
                && (squad.assignedObjective.kind() == AssignmentKind.HOLD_NODE
                || squad.assignedObjective.kind() == AssignmentKind.CLEAR_ZONE)
                && squad.originalDirective != null
                && squad.originalDirective.authority()
                == CommandAuthority.MISSION_COMMAND
                && strategyId().equals(squad.originalDirective.issuer());
    }

    @Override
    public DeliveryDeployment deploymentFor(ReinforcementRequest request) {
        int sourceX = request.hasObjective() ? request.objectiveX : request.rallyX;
        int sourceY = request.hasObjective() ? request.objectiveY : request.rallyY;
        int track = trackLayout.trackForCell(sourceX, sourceY);
        if (track < 0 || track >= trackLayout.trackCount()) {
            return DeliveryDeployment.legacy(request);
        }

        int requestedForward = Math.round(trackLayout.forwardCoordinate(
                request.rallyX, request.rallyY));
        int minimumForward = requestedForward;
        if (request.hasObjective()) {
            int objectiveForward = Math.round(trackLayout.forwardCoordinate(
                    request.objectiveX, request.objectiveY));
            minimumForward = Math.max(minimumForward,
                    objectiveForward + CONVOY_REAR_STANDOFF_CELLS);
        }
        TrackState state = frontSnapshot != null ? frontSnapshot.track(track) : null;
        if (state != null && state.knownHostileFrontProgress() >= 0f) {
            int hostileForward = Math.round(state.knownHostileFrontProgress()
                    * (trackLayout.forwardExtent() - 1));
            minimumForward = Math.max(minimumForward,
                    hostileForward + CONVOY_REAR_STANDOFF_CELLS);
        }
        minimumForward = Math.max(0, Math.min(
                trackLayout.forwardExtent() - 1, minimumForward));
        minimumForward = Math.min(trackLayout.forwardExtent() - 1,
                ((minimumForward + CONVOY_DEPLOYMENT_BAND_CELLS - 1)
                        / CONVOY_DEPLOYMENT_BAND_CELLS)
                        * CONVOY_DEPLOYMENT_BAND_CELLS);

        int lateral = Math.round(trackLayout.lateralCoordinate(
                request.rallyX, request.rallyY));
        lateral = Math.max(trackLayout.lateralStartInclusive(track),
                Math.min(trackLayout.lateralEndInclusive(track), lateral));
        int hintX = trackLayout.cellX(lateral, minimumForward);
        int hintY = trackLayout.cellY(lateral, minimumForward);
        return new DeliveryDeployment(hintX, hintY, minimumForward,
                true, request.hasObjective(),
                SquadCommandClaim.mission(strategyId(),
                        "convoy relief " + request.reason.name()));
    }

    /**
     * Whether belts are manned reactively this pulse: the mode asks for it, the map has garrisonables, and the influence
     * field's block lattice is the one the registry was laid out on.
     */
    private static boolean reactiveManning(GarrisonableRegistry places, CommanderInfluenceSnapshot influence) {
        return places != null && places.count() > 0
                && influence != null && influence.blockSize() == places.blockSize()
                && influence.width() == places.blockWidth() && influence.height() == places.blockHeight();
    }

    /**
     * The garrisonable this squad is manning or has been ordered to man, or {@code null} when it is an ordinary patrol.
     * A squad carrying the run-scoped area order is manning it; one the director ordered that the ledger has not yet
     * shown carrying the order is still counted. The place is found again by its centre cell on the current index, so a
     * run renumbered or shortened by shelling is followed; a place that is gone releases a squad free to move.
     */
    /** Largest circle of the area defence a squad driven off a compound's apron holds inside the building. UNTUNED. */
    static final int INSIDE_RADIUS_CELLS = 6;

    /** The compounds this plan was made against, for the fall back from an apron into its building. */
    private List<ConquestCommandFacts.Compound> compoundsThisPlan = List.of();

    /** Plans a squad that fell back inside a compound stays there once its building is quiet. UNTUNED. */
    static final int INSIDE_COOL_PLANS = 8;

    /** A squad driven off a compound's apron into the building it fronts: the order it holds there and the building. */
    private static final class Inside {
        final ObjectiveAssignment order;
        final TacticalNode building;
        int quietPlans;

        Inside(ObjectiveAssignment order, TacticalNode building) {
            this.order = order;
            this.building = building;
        }
    }

    /** Squads holding inside a compound after falling back off its apron, by squad id. */
    private final Map<Integer, Inside> insideFallBacks = new TreeMap<>();

    private GarrisonDirector.Garrison garrisonOf(PlanningSquad squad, GarrisonableRegistry places) {
        ObjectiveAssignment order = squad.assignedObjective;
        int cellX;
        int cellY;
        if (order != null && order.mansPlace()) {
            cellX = order.targetCellX();
            cellY = order.targetCellY();
            garrisons.adopted(squad.id);
        } else {
            int[] cell = garrisons.pendingCell(squad.id);
            if (cell == null) return null;
            cellX = cell[0];
            cellY = cell[1];
        }
        int place = places.idAt(cellX, cellY);
        if (place >= 0) {
            int reach = places.radius(place);
            boolean arrived = Math.abs(squad.centroidX - places.centreX(place)) <= reach
                    && Math.abs(squad.centroidY - places.centreY(place)) <= reach;
            return new GarrisonDirector.Garrison(squad.id, place, !squad.localContact, arrived, squad.aliveMembers,
                    (int) squad.centroidX, (int) squad.centroidY, squad.originalMembers);
        }
        garrisons.forget(squad.id);
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        if (profile != null) profile.recordCount(TickInnerProfile.Bucket.GARRISON_LOST, 1);
        if (!squad.localContact && order != null && order.mansPlace()) squad.assignedObjective = null;
        return null;
    }

    /**
     * Prices the garrisonables believed pressure has reached, then keeps each manning squad on its place or gives it
     * back to the pool (and so to this pulse's response) once the place has stayed cool for the dwell.
     */
    private void standOrRelease(GarrisonableRegistry places, CommanderInfluenceSnapshot influence,
                                List<GarrisonDirector.Garrison> held, Map<Integer, PlanningSquad> heldSquads,
                                List<PlanningSquad> candidates, Map<Integer, SquadDirective> directives,
                                Set<Integer> justReleased, CommandTopology topology, Faction perspective) {
        int count = 0;
        for (CommanderContact contact : influence.contacts()) {
            if (contact.confidence() * contact.strength() < GarrisonDirector.MIN_CONTACT_WEIGHT) continue;
            if (2 * count + 2 > contactScratch.length) {
                contactScratch = Arrays.copyOf(contactScratch, contactScratch.length * 2);
            }
            contactScratch[2 * count] = contact.cellX();
            contactScratch[2 * count + 1] = contact.cellY();
            count++;
        }
        Set<Integer> releasing = new HashSet<>(garrisons.evaluate(places, new GarrisonDirector.Pressure() {
            @Override public float hostileAt(int bx, int by) { return influence.hostileAt(bx, by); }
            @Override public float floorAt(int bx, int by) { return alarms.floorAt(bx, by); }
        }, contactScratch, count, held));
        Set<Integer> fallingBack = new HashSet<>(garrisons.fallingBack());
        Map<Integer, Integer> shiftedTo = new HashMap<>();
        for (GarrisonDirector.Shift shift : garrisons.shifting()) shiftedTo.put(shift.squadId(), shift.to());
        for (GarrisonDirector.Garrison garrison : held) {
            PlanningSquad squad = heldSquads.get(garrison.squadId());
            int home = homeTrack(squad);
            if (fallingBack.contains(squad.id)) {
                ObjectiveAssignment post = fallBackPost(squad, garrison.garrisonable(), places, topology, perspective);
                if (post != null) {
                    squad.assignedObjective = post;
                    garrisons.forget(squad.id);
                    directives.put(squad.id, directive(squad, home, home, AssignmentReason.DEFENDER_GARRISONABLE_FALL_BACK));
                    continue;
                }
                releasing.add(squad.id);
            }
            if (releasing.contains(squad.id)) {
                squad.assignedObjective = null;
                garrisons.forget(squad.id);
                candidates.add(squad);
                justReleased.add(squad.id);
                continue;
            }
            // A garrison shifting onto the next stretch of its run keeps its assignment; only the stretch moves.
            int place = shiftedTo.getOrDefault(squad.id, garrison.garrisonable());
            ObjectiveAssignment order = runOrder(squad.id, place, places);
            if (!order.equals(squad.assignedObjective)) squad.assignedObjective = order;
            directives.put(squad.id, directive(squad, home, home, squad.localContact
                    ? AssignmentReason.DEFENDER_LOCAL_CONTACT : AssignmentReason.DEFENDER_GARRISONABLE_STAND));
        }
    }

    /** The order that mans a stretch: an area defence on its circle, its claims kept to the stretch's run. */
    private static ObjectiveAssignment runOrder(int squadId, int place, GarrisonableRegistry places) {
        return GarrisonDirector.orderFor(squadId, place, places);
    }

    /**
     * Waiting patrols that are not in this plan's candidate list because they stood on their posts at its start. They
     * are still in the mobile pool, and still reserve: staged, not committed.
     */
    private int stagedOutside(List<PlanningSquad> candidates) {
        if (stagers.isEmpty()) return 0;
        int outside = stagers.size();
        for (PlanningSquad squad : candidates) if (stagers.containsKey(squad.id)) outside--;
        return outside;
    }

    /** Whether this squad is one of this commander's waiting patrols, carrying the area defence its post gave it. */
    private boolean isStagePost(PlanningSquad squad) {
        StagePost post = stagers.get(squad.id);
        return post != null && stageStands(squad, post);
    }

    /** Whether a patrol still waits as ordered: carrying the area defence its post gave it. */
    private static boolean stageStands(PlanningSquad squad, StagePost post) {
        ObjectiveAssignment order = squad.assignedObjective;
        return order != null && order.kind() == AssignmentKind.DEFEND_AREA
                && order.targetCellX() == post.order.targetCellX() && order.targetCellY() == post.order.targetCellY();
    }

    /**
     * Whether a squad that fell back off a compound's apron still holds inside the building: while it carries the order
     * it was given there, the marines have not taken the compound, and the building has not been quiet for
     * {@link #INSIDE_COOL_PLANS} plans (never released in a fight). A squad that stops holding is forgotten and planned
     * like any other patrol.
     */
    private boolean keepsInside(PlanningSquad squad, CommanderInfluenceSnapshot influence) {
        Inside inside = insideFallBacks.get(squad.id);
        if (inside == null) return false;
        if (!inside.order.equals(squad.assignedObjective)) {
            insideFallBacks.remove(squad.id);
            return false;
        }
        boolean lost = false;
        for (ConquestCommandFacts.Compound compound : compoundsThisPlan) {
            if (GarrisonableRegistry.sameBuilding(compound.node(), inside.building)) {
                lost = compound.state() == CompoundService.CompoundState.MARINE_HELD;
            }
        }
        boolean quiet = influence == null || placePressure(influence,
                inside.building.anchorX / influence.blockSize(), inside.building.anchorY / influence.blockSize())
                < GarrisonDirector.RELEASE_PRESSURE;
        inside.quietPlans = quiet && !squad.localContact ? inside.quietPlans + 1 : 0;
        if (lost || inside.quietPlans >= INSIDE_COOL_PLANS) {
            insideFallBacks.remove(squad.id);
            squad.assignedObjective = null;
            return false;
        }
        return true;
    }

    /** Whether this order is the one a squad was given inside a compound when it fell back off the apron. */
    private boolean isInsideFallBack(PlanningSquad squad) {
        Inside inside = insideFallBacks.get(squad.id);
        return inside != null && inside.order.equals(squad.assignedObjective);
    }

    /** The works (lowest cell) of each fort this side holds, by fort id. */
    private Map<Integer, DefenceStructureFact> heldWorks(Faction perspective) {
        Map<Integer, DefenceStructureFact> works = new TreeMap<>();
        for (DefenceStructureFact structure : structures) {
            if (!structure.ofFort() || !perspective.friendlyTo(structure.holder())) continue;
            works.merge(structure.fortId(), structure, (a, b) ->
                    b.cellY() < a.cellY() || (b.cellY() == a.cellY() && b.cellX() < a.cellX()) ? b : a);
        }
        return works;
    }

    /**
     * The believed pressure areas of this plan: the distinct influence blocks holding a believed contact (the weak ones
     * a works crew or a fading report would add are ignored, as they are for manning), clustered by {@link GarrisonFlow}.
     */
    /**
     * The pressure this side reads on a block: what it believes is there, or the floor an alarm on one of its own
     * places keeps under it, whichever is greater.
     */
    private float placePressure(CommanderInfluenceSnapshot influence, int bx, int by) {
        return Math.max(influence.hostileAt(bx, by), alarms.floorAt(bx, by));
    }

    private List<GarrisonFlow.Area> pressureAreas(CommanderInfluenceSnapshot influence) {
        int bw = influence.width();
        int bh = influence.height();
        int size = influence.blockSize();
        if (flowStamp.length != bw * bh) {
            flowStamp = new int[bw * bh];
            flowGeneration = 0;
        }
        flowGeneration++;
        int count = 0;
        for (CommanderContact contact : influence.contacts()) {
            if (contact.confidence() * contact.strength() < GarrisonDirector.MIN_CONTACT_WEIGHT) continue;
            if (onTurnedGround(contact)) continue;
            int bx = Math.min(bw - 1, Math.max(0, contact.cellX() / size));
            int by = Math.min(bh - 1, Math.max(0, contact.cellY() / size));
            int block = by * bw + bx;
            if (flowStamp[block] == flowGeneration) continue;
            flowStamp[block] = flowGeneration;
            if (count == flowBlocks.length) {
                flowBlocks = Arrays.copyOf(flowBlocks, count * 2);
                flowPressure = Arrays.copyOf(flowPressure, count * 2);
            }
            flowBlocks[count] = block;
            flowPressure[count] = placePressure(influence, bx, by);
            count++;
        }
        // An alarmed fort is a pressure area whether or not anybody can see who is in it.
        for (int[] alarmed : alarms.alarmedBlocks()) {
            int bx = Math.min(bw - 1, Math.max(0, alarmed[0]));
            int by = Math.min(bh - 1, Math.max(0, alarmed[1]));
            if (onTurnedGround(
                    influence.blockWorldX(bx) + influence.blockWorldWidth(bx) / 2,
                    influence.blockWorldY(by) + influence.blockWorldHeight(by) / 2)) continue;
            int block = by * bw + bx;
            if (flowStamp[block] == flowGeneration) continue;
            flowStamp[block] = flowGeneration;
            if (count == flowBlocks.length) {
                flowBlocks = Arrays.copyOf(flowBlocks, count * 2);
                flowPressure = Arrays.copyOf(flowPressure, count * 2);
            }
            flowBlocks[count] = block;
            flowPressure[count] = placePressure(influence, bx, by);
            count++;
        }
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        if (profile != null) profile.recordCount(TickInnerProfile.Bucket.FLOW_BLOCK, count);
        return GarrisonFlow.areas(flowBlocks, flowPressure, count, bw, size);
    }

    /**
     * A directive that says a squad is staging, manning or standing on a place must carry that squad's order. One that
     * does not (the squad lost its order after the directive was written in the same plan) would publish a reason with no
     * assignment cell and propose a release under the wrong name, so it is rewritten as the reserve hold it has become.
     */
    private void reconcileOrderlessDirectives(Map<Integer, PlanningSquad> allSquads, Map<Integer, SquadDirective> directives) {
        for (Map.Entry<Integer, SquadDirective> entry : directives.entrySet()) {
            AssignmentReason reason = entry.getValue().reason();
            if (reason != AssignmentReason.DEFENDER_GARRISONABLE_STAGE
                    && reason != AssignmentReason.DEFENDER_GARRISONABLE_MAN
                    && reason != AssignmentReason.DEFENDER_GARRISONABLE_STAND) {
                continue;
            }
            PlanningSquad squad = allSquads.get(entry.getKey());
            if (squad == null || squad.assignedObjective != null) continue;
            int home = homeTrack(squad);
            entry.setValue(directive(squad, home, home, AssignmentReason.DEFENDER_RESERVE_HOLD));
        }
    }

    /** Forgets waiting patrols whose squad is gone. */
    private void dropDeadStagers(Map<Integer, PlanningSquad> allSquads) {
        insideFallBacks.keySet().removeIf(id -> {
            PlanningSquad squad = allSquads.get(id);
            return squad == null || squad.aliveMembers <= 0;
        });
        stagers.keySet().removeIf(id -> {
            PlanningSquad squad = allSquads.get(id);
            return squad == null || squad.aliveMembers <= 0;
        });
    }

    /**
     * Finds the plan's pressure areas and what each offers: whether it has a garrisonable under believed pressure to man,
     * the held fort's works that would shelter it, and the cell its squads wait on. An area that offers nothing to man
     * gets no staging cell, so nothing is staged for it and the track rally answers it alone.
     */
    private AreaPlan buildAreaPlan(CommanderInfluenceSnapshot influence, GarrisonableRegistry places,
                                   CommandTopology topology, Faction perspective) {
        AreaPlan plan = new AreaPlan(pressureAreas(influence));
        int n = plan.size();
        if (places != null) {
            for (int place : garrisons.warmPlaces()) {
                int a = GarrisonFlow.matchingArea(plan.areas, places.centreX(place), places.centreY(place));
                if (a < 0) continue;
                plan.workful[a] = true;
                int fort = places.fortId(place);
                if (fort >= 0 && (plan.warmFort[a] < 0 || fort < plan.warmFort[a])) plan.warmFort[a] = fort;
            }
        }
        Map<Integer, DefenceStructureFact> works = null;
        int workless = 0;
        for (int i = 0; i < n; i++) {
            GarrisonFlow.Area area = plan.areas.get(i);
            if (area.weight() < GarrisonFlow.AREA_START_WEIGHT) continue;
            if (!plan.workful[i]) {
                workless++;
                continue;
            }
            if (plan.warmFort[i] >= 0) {
                if (works == null) works = heldWorks(perspective);
                plan.site[i] = works.get(plan.warmFort[i]);
            }
            if (plan.site[i] != null) {
                plan.stage[i] = new int[]{plan.site[i].cellX(), plan.site[i].cellY()};
                continue;
            }
            plan.stage[i] = stageCell(influence, topology, area, 0);
        }
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        if (profile != null && workless > 0) profile.recordCount(TickInnerProfile.Bucket.FLOW_WORKLESS_AREA, workless);
        return plan;
    }

    /**
     * The anticipation tier (see {@link GarrisonFlow}): works out how many squads each pressure area wants and has
     * reserved, and posts free patrols of the pool at a staging cell on the defender's side of the heaviest unmet areas,
     * so that manning and structure work find somebody near. A fort's works are the staging cell for an area that holds
     * one of the fort's pressed places. Patrols stay in the pool and keep their rule of being the first candidates for
     * local assignment; they are given back after their area has been quiet for {@link #STAGE_COOL_PLANS} plans. Runs
     * after the track response and structure work have chosen, so it only ever spends what they left, and never empties
     * the roving reserve.
     *
     * <p>It shares one reservation per area with the rally (see {@link AreaReservations}): the squads the rally already
     * brought to an area count against that area's demand, and it stages only for areas with something to man.
     */
    private void flowToPressure(CommanderInfluenceSnapshot influence, GarrisonableRegistry places,
                                CommandTopology topology, Faction perspective,
                                List<GarrisonDirector.Garrison> held, List<PlanningSquad> candidates,
                                Map<Integer, PlanningSquad> staged, Map<Integer, PlanningSquad> allSquads,
                                Set<Integer> selected, Set<Integer> justReleased,
                                Map<Integer, SquadDirective> directives) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long started = profile != null ? System.nanoTime() : 0L;
        dropDeadStagers(allSquads);
        AreaPlan plan = areaPlan;
        List<GarrisonFlow.Area> areas = plan.areas;
        int n = areas.size();
        if (n == 0 && stagers.isEmpty()) {
            if (profile != null) profile.record(TickInnerProfile.Bucket.FLOW_EVAL, System.nanoTime() - started);
            return;
        }

        // What each area has: pressed places nobody stands on, and squads already waiting for it.
        int[] unmanned = new int[n];
        int[] waiting = new int[n];
        for (int i = 0; i < n; i++) waiting[i] = reservations.count(i, AreaReservations.Kind.STAGED);
        if (places != null) {
            for (int place : garrisons.hotPlaces()) {
                if (garrisons.isManned(place)) continue;
                int a = GarrisonFlow.matchingArea(areas, places.centreX(place), places.centreY(place));
                if (a >= 0) unmanned[a]++;
            }
        }
        int released = 0;
        for (Integer id : new TreeSet<>(stagers.keySet())) {
            StagePost post = stagers.get(id);
            int a = GarrisonFlow.matchingArea(areas, post.areaX, post.areaY);
            if (a >= 0) {
                post.quietPlans = 0;
                continue;
            }
            if (++post.quietPlans < STAGE_COOL_PLANS) continue;
            PlanningSquad squad = allSquads.get(id);
            stagers.remove(id);
            staged.remove(id);
            reservations.release(id);
            if (stageStands(squad, post)) squad.assignedObjective = null;
            if (!squad.localContact) candidates.add(squad);
            released++;
        }

        DefenceStructureFact[] site = plan.site;
        int[][] stage = plan.stage;

        // Free squads already near an area are local to it: nothing needs to be moved for them.
        int[] nearby = new int[n];
        int etaEvals = 0;
        for (PlanningSquad squad : candidates) {
            if (selected.contains(squad.id) || squad.crew || squad.machine || justReleased.contains(squad.id)) continue;
            int best = -1;
            int bestEta = Integer.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (stage[i] == null) continue;
                etaEvals++;
                int eta = GarrisonDirector.etaTicks(squad.anchorCellX, squad.anchorCellY, stage[i][0], stage[i][1]);
                if (eta <= GarrisonFlow.LOCAL_ETA_TICKS && eta < bestEta) {
                    bestEta = eta;
                    best = i;
                }
            }
            if (best >= 0) nearby[best]++;
        }

        int[] demand = new int[n];
        int[] local = new int[n];
        int[] unmet = new int[n];
        int totalDemand = 0;
        int totalLocal = 0;
        for (int i = 0; i < n; i++) {
            if (stage[i] == null) continue;
            demand[i] = GarrisonFlow.demand(areas.get(i).weight(), unmanned[i]);
            local[i] = reservations.reserved(i) + nearby[i];
            totalDemand += demand[i];
            totalLocal += Math.min(demand[i], local[i]);
            if (stageRetryAt.getOrDefault(retryKey(areas.get(i)), 0) <= planCount) {
                unmet[i] = Math.max(0, demand[i] - local[i]);
            }
        }
        stageRetryAt.values().removeIf(at -> at <= planCount);

        // Bounded per plan and in all; pickets before massing across areas.
        int poolSize = candidates.size() + stagers.size() + held.size();
        int allowed = Math.max(0, GarrisonFlow.stagedCap(poolSize) - stagers.size());
        int[] order = GarrisonFlow.sendOrder(unmet, local, Math.min(MAX_STAGE_PER_PLAN, allowed));
        boolean[] failed = new boolean[n];
        int newly = 0;
        int tried = 0;
        for (int i : order) {
            if (failed[i]) continue;
            List<PlanningSquad> pool = new ArrayList<>();
            for (PlanningSquad squad : candidates) {
                if (!selected.contains(squad.id) && !squad.crew && !squad.machine && !justReleased.contains(squad.id)) {
                    pool.add(squad);
                }
            }
            // A patrol is never taken below the roving reserve the response itself keeps.
            if (pool.size() <= GarrisonDirector.RESERVE_FLOOR) break;
            int[] base = stage[i];
            pool.sort(Comparator.<PlanningSquad>comparingLong(squad -> {
                long dx = squad.anchorCellX - base[0];
                long dy = squad.anchorCellY - base[1];
                return dx * dx + dy * dy;
            }).thenComparingInt(squad -> squad.id));
            boolean posted = false;
            int priced = 0;
            for (PlanningSquad squad : pool) {
                if (priced++ >= STAGE_CANDIDATES_TRIED) break;
                tried++;
                ObjectiveAssignment post = site[i] != null
                        ? DefenceStructureTactics.doorstepPost(squad.id, site[i], squad.anchorCellX, squad.anchorCellY, topology)
                        : generalPost(squad, influence, topology, areas.get(i), base, waiting[i]);
                if (post == null) continue;
                squad.assignedObjective = post;
                int home = homeTrack(squad);
                directives.put(squad.id, directive(squad, home, home, AssignmentReason.DEFENDER_GARRISONABLE_STAGE));
                selected.add(squad.id);
                stagers.put(squad.id, new StagePost(post, areas.get(i).centreX(), areas.get(i).centreY()));
                reservations.commit(squad.id, i, AreaReservations.Kind.STAGED);
                waiting[i]++;
                local[i]++;
                posted = true;
                newly++;
                break;
            }
            if (!posted) {
                failed[i] = true;
                stageRetryAt.put(retryKey(areas.get(i)), planCount + STAGE_RETRY_PLANS);
            }
        }

        if (profile != null) {
            profile.recordCount(TickInnerProfile.Bucket.FLOW_AREA, n);
            profile.recordCount(TickInnerProfile.Bucket.FLOW_DEMAND, totalDemand);
            profile.recordCount(TickInnerProfile.Bucket.FLOW_LOCAL, totalLocal);
            profile.recordCount(TickInnerProfile.Bucket.FLOW_RELEASE, released);
            profile.recordCount(TickInnerProfile.Bucket.FLOW_ETA_EVAL, etaEvals);
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_STAGE, newly);
            profile.recordCount(TickInnerProfile.Bucket.GARRISON_STAGE_EVAL, tried);
            profile.recordCount(TickInnerProfile.Bucket.FLOW_RESERVED, reservations.totalReserved());
            profile.recordCount(TickInnerProfile.Bucket.FLOW_RALLY_AREA, rallyRedirects);
            profile.record(TickInnerProfile.Bucket.FLOW_EVAL, System.nanoTime() - started);
        }
        StringBuilder note = new StringBuilder("flow areas=").append(n).append(" staged=").append(stagers.size())
                .append(" new=").append(newly).append(" released=").append(released);
        note.append(" rallyToArea=").append(rallyRedirects);
        for (int i = 0; i < n; i++) {
            GarrisonFlow.Area area = areas.get(i);
            note.append(" [").append(area.centreX()).append(',').append(area.centreY())
                    .append(" w").append(Math.round(area.weight()))
                    .append(" d").append(demand[i]).append(" l").append(local[i]);
            note.append(" m").append(reservations.count(i, AreaReservations.Kind.MANNED))
                    .append(" s").append(reservations.count(i, AreaReservations.Kind.STAGED))
                    .append(" r").append(reservations.count(i, AreaReservations.Kind.RALLIED));
            if (!plan.workful[i]) note.append(" bare");
            note.append(site[i] != null ? " works" : "").append(']');
        }
        flowNote = note.toString();
    }

    /** Identifies an area across plans well enough to back off retrying it: its centre on a 32-cell lattice. */
    private static long retryKey(GarrisonFlow.Area area) {
        return ((long) (area.centreY() / 32) << 32) | ((area.centreX() / 32) & 0xffffffffL);
    }

    /**
     * The staging cell for an area, on the defender's side of its pressure: a walkable cell with little believed
     * hostile pressure nearest the desired one, or {@code null} when none lies within reach.
     *
     * @param spread which of the area's waiting squads this is, so that they do not share one cell
     */
    private int[] stageCell(CommanderInfluenceSnapshot influence, CommandTopology topology, GarrisonFlow.Area area,
                            int spread) {
        int[] desired = GarrisonFlow.desiredStage(trackLayout, area.centreX(), area.centreY(), spread);
        int[] quiet = GarrisonFlow.snap(desired[0], desired[1], STAGE_SNAP_RADIUS, (x, y) ->
                topology.inBounds(x, y) && topology.isWalkable(x, y)
                        && influence.hostileAtWorld(x, y) < GarrisonDirector.FLOW_PRESSURE);
        if (quiet != null) return quiet;
        return GarrisonFlow.snap(desired[0], desired[1], STAGE_SNAP_RADIUS, (x, y) ->
                topology.inBounds(x, y) && topology.isWalkable(x, y));
    }

    /** An area defence at the area's staging cell (offset by how many already wait there) if this squad can reach it. */
    private ObjectiveAssignment generalPost(PlanningSquad squad, CommanderInfluenceSnapshot influence,
                                            CommandTopology topology, GarrisonFlow.Area area, int[] base, int waiting) {
        int[] cell = waiting == 0 ? base : stageCell(influence, topology, area, waiting);
        if (cell == null) return null;
        int startX = squad.anchorCellX;
        int startY = squad.anchorCellY;
        if (!topology.inBounds(startX, startY) || !topology.isWalkable(startX, startY)) return null;
        if (!(startX == cell[0] && startY == cell[1]) && !topology.reachable(startX, startY, cell[0], cell[1])) return null;
        return ObjectiveAssignment.defendArea(squad.id, cell[0], cell[1], STAGE_RADIUS_CELLS);
    }

    /**
     * Where a garrison whose ground is lost withdraws to: an area defence at the doorstep of the nearest standing work
     * of the same fort this side holds; for a compound's apron, an area defence inside the compound's own building while the
     * marines have not taken it; or {@code null} when the place belongs to neither or nothing of it stands, in which
     * case the squad is released to the pool instead. Squad-tier movement under the commander's authority; the
     * order is the ordinary area defence.
     */
    private ObjectiveAssignment fallBackPost(PlanningSquad squad, int place, GarrisonableRegistry places,
                                             CommandTopology topology, Faction perspective) {
        int fort = places.fortId(place);
        if (fort < 0) {
            // A compound's apron is the layer in front of its building, and the building's envelope the layer in front
            // of its rooms: driven off the apron the squad mans the envelope, driven out of the envelope it holds the
            // rooms, while its own side still holds the compound.
            ConquestCommandFacts.Compound compound = CompoundGround.frontedBy(places, place, compoundsThisPlan);
            if (compound == null || compound.state() == CompoundService.CompoundState.MARINE_HELD) return null;
            TacticalNode building = compound.node();
            int envelope = places.isEnvelope(place) ? -1 : places.envelopeOf(building);
            if (envelope >= 0 && !garrisons.lockedOut(places.centreX(envelope), places.centreY(envelope))) {
                return GarrisonDirector.orderFor(squad.id, envelope, places);
            }
            int radius = Math.max(2, Math.min(INSIDE_RADIUS_CELLS,
                    Math.min(building.right - building.left, building.bottom - building.top) / 2));
            ObjectiveAssignment inside = ObjectiveAssignment.defendArea(squad.id, building.anchorX, building.anchorY, radius);
            insideFallBacks.put(squad.id, new Inside(inside, building));
            return inside;
        }
        DefenceStructureFact works = null;
        long best = Long.MAX_VALUE;
        for (DefenceStructureFact structure : structures) {
            if (structure.fortId() != fort || !perspective.friendlyTo(structure.holder())) continue;
            long dx = structure.cellX() - places.centreX(place);
            long dy = structure.cellY() - places.centreY(place);
            long gap = dx * dx + dy * dy;
            if (gap < best) {
                best = gap;
                works = structure;
            }
        }
        if (works == null) return null;
        return DefenceStructureTactics.doorstepPost(squad.id, works, squad.anchorCellX, squad.anchorCellY, topology);
    }

    private static boolean isTrackResponse(SquadDirective directive) {
        return directive != null && (directive.reason() == AssignmentReason.DEFENDER_TRACK_RESPONSE
                || directive.reason() == AssignmentReason.DEFENDER_ADJACENT_TRACK_RESPONSE);
    }

    /**
     * Sends free patrols, staged patrols and patrols the track response has just rallied to the garrisonables under
     * pressure, nearest first, pickets before massing, never emptying the reserve. Reach follows the response's own
     * rule: the squad's home track within one of the place's, and a walkable route to it.
     */
    private List<GarrisonDirector.Man> manGarrisonables(GarrisonableRegistry places, CommandTopology topology,
                                  List<PlanningSquad> candidates, Map<Integer, PlanningSquad> staged,
                                  Map<Integer, PlanningSquad> allSquads,
                                  Set<Integer> selected, Set<Integer> justReleased,
                                  Map<Integer, SquadDirective> directives) {
        List<GarrisonDirector.Free> free = new ArrayList<>();
        for (PlanningSquad squad : candidates) {
            if (squad.crew || squad.machine || justReleased.contains(squad.id)) continue;
            if (!selected.contains(squad.id)) {
                free.add(new GarrisonDirector.Free(squad.id, squad.anchorCellX, squad.anchorCellY, false, squad.aliveMembers, squad.originalMembers));
            } else if (isTrackResponse(directives.get(squad.id))) {
                // Already marching toward the threatened track: a pressed place on the way is a better post than the
                // track point. Not part of the roving reserve, so the floor does not bar it.
                free.add(new GarrisonDirector.Free(squad.id, squad.anchorCellX, squad.anchorCellY, true, squad.aliveMembers, squad.originalMembers));
            }
        }
        for (PlanningSquad squad : staged.values()) {
            free.add(new GarrisonDirector.Free(squad.id, squad.anchorCellX, squad.anchorCellY, true, squad.aliveMembers, squad.originalMembers));
        }
        // The counter-attack's reserve above the law's share may man its own fort's belt, and nothing else.
        Set<Integer> lent = new HashSet<>(counter.lendable());
        for (int id : lent) {
            PlanningSquad squad = allSquads.get(id);
            if (squad == null || squad.crew || squad.machine) continue;
            free.add(new GarrisonDirector.Free(squad.id, squad.anchorCellX, squad.anchorCellY, true, squad.aliveMembers, squad.originalMembers));
        }
        String counterPlace = counter.placeKey();
        List<GarrisonDirector.Man> sent = garrisons.assign(free, (squadId, place) -> {
            if (turnedFortIds.contains(places.fortId(place))) return false;
            // Inside a lost place's ground nothing is manned, claimed or not: a post among its holders is not a retake.
            if (!lostGround.isEmpty() && PlaceCounterattack.onTurnedGround(lostGround, counterPlaces,
                    places.centreX(place), places.centreY(place))) return false;
            if (lent.contains(squadId) && !belongsTo(places, place, counterPlace)) return false;
            PlanningSquad squad = allSquads.get(squadId);
            int x = places.centreX(place);
            int y = places.centreY(place);
            int track = trackLayout.trackForCell(x, y);
            if (!staged.containsKey(squadId) && track >= 0 && Math.abs(homeTrack(squad) - track) > 1) return false;
            int startX = squad.anchorCellX;
            int startY = squad.anchorCellY;
            return topology.inBounds(startX, startY) && topology.isWalkable(startX, startY)
                    && (startX == x && startY == y || topology.reachable(startX, startY, x, y));
        }, areaPlan == null ? null : (squadId, place) -> {
            // The squads reserved to the place's own area are drawn on before any other.
            int own = reservations.areaOf(squadId);
            return own >= 0 && own == GarrisonFlow.matchingArea(areaPlan.areas, places.centreX(place), places.centreY(place));
        });
        for (GarrisonDirector.Man man : sent) {
            PlanningSquad squad = allSquads.get(man.squadId());
            if (lent.contains(squad.id)) counter.lend(squad.id);
            squad.assignedObjective = runOrder(squad.id, man.garrisonable(), places);
            int home = homeTrack(squad);
            directives.put(squad.id, directive(squad, home, home, AssignmentReason.DEFENDER_GARRISONABLE_MAN));
            selected.add(squad.id);
            stagers.remove(squad.id);
            if (areaPlan != null) {
                int a = GarrisonFlow.matchingArea(areaPlan.areas, places.centreX(man.garrisonable()),
                        places.centreY(man.garrisonable()));
                // Manned now, whatever it was reserved as before: the table counts a squad once.
                reservations.commit(squad.id, a >= 0 ? a : reservations.areaOf(squad.id), AreaReservations.Kind.MANNED);
            }
        }
        return sent;
    }

    /**
     * The reserve the track response keeps with the counter-attack on: while the counter-attack's reserve waits, it is
     * part of the reserve law's share rather than in addition to it; once a group is mustering or launched, the response
     * keeps the law's share of what is left.
     */
    private int counterReserve(int candidateCount) {
        int held = counter.heldInReserve();
        if (held == 0) return mobileReserveFor(candidateCount);
        return Math.max(0, mobileReserveFor(candidateCount + held) - held);
    }

    /**
     * One pulse of the counter-attack ({@link PlaceCounterattack}): offers it the squads it holds and the free patrols the
     * tracks' pickets left, takes back what it recruits and releases, and writes its orders. Recruits leave the candidate
     * list; released squads join it, free for the rest of this plan.
     */
    private List<String> planCounterattack(ConquestCommandFrame frame, CommanderInfluenceSnapshot influence,
                                           List<PlanningSquad> candidates, Map<Integer, PlanningSquad> countering,
                                           Set<Integer> selected, Set<Integer> justReleased, Map<Integer, SquadDirective> directives) {
        List<PlaceCounterattack.Body> members = new ArrayList<>();
        for (PlanningSquad squad : countering.values()) members.add(counterBody(squad));
        List<PlaceCounterattack.Body> free = new ArrayList<>();
        Map<Integer, PlanningSquad> byId = new HashMap<>();
        for (PlanningSquad squad : candidates) {
            // A garrison just given back or falling back into its building is still about its place, not free.
            if (selected.contains(squad.id) || squad.crew || justReleased.contains(squad.id) || isInsideFallBack(squad)) {
                continue;
            }
            free.add(counterBody(squad));
            byId.put(squad.id, squad);
        }
        List<PlaceCounterattack.Hostile> hostiles = PlaceCounterattack.hostiles(influence);
        // Every own squad watches whatever it stands near: a picket, a garrison, a recon car or a drone flight observes a
        // lost place as well as a probe does.
        List<int[]> watchers = new ArrayList<>();
        for (CommandSquadState state : frame.squads()) {
            if (state.aliveMembers() > 0) watchers.add(new int[] {state.anchorCellX(), state.anchorCellY()});
        }
        int pool = candidates.size() + countering.size();
        Map<Integer, PlanningSquad> sortieSquads = sortieGarrisons(frame, hostiles);
        List<PlaceCounterattack.Body> sortie = new ArrayList<>();
        for (PlanningSquad squad : sortieSquads.values()) sortie.add(counterBody(squad));
        PlaceCounterattack.Result result = counter.plan(frame.tick(), counterPlaces, keepCell(frame), hostiles, watchers,
                PlaceCounterattack.losses(influence), members, free, sortie, mobileReserveFor(pool), pool,
                frame.topology());
        List<Integer> handoffs = new ArrayList<>();
        for (int id : result.recruited()) {
            if (sortieSquads.containsKey(id)) {
                // A garrison is its own authority's until it is handed over; from the next pulse it is the group's.
                handoffs.add(id);
                continue;
            }
            PlanningSquad squad = byId.get(id);
            candidates.remove(squad);
            countering.put(id, squad);
        }
        for (int id : result.released()) {
            PlanningSquad squad = countering.remove(id);
            if (squad == null) continue;
            if (squad.assignedObjective != null) counterLeft.put(id, squad.assignedObjective);
            // The staging post (or the retake order) was the counter-attack's; a squad given back holds nothing of it.
            if (squad.assignedObjective != null && !squad.assignedObjective.isGarrison()) {
                squad.assignedObjective = null;
            }
            candidates.add(squad);
        }
        for (PlanningSquad squad : countering.values()) {
            PlaceCounterattack.Order order = result.orders().get(squad.id);
            int home = homeTrack(squad);
            if (order == null) {
                directives.put(squad.id, directive(squad, home, home, AssignmentReason.DEFENDER_COUNTERATTACK_RESERVE));
                continue;
            }
            squad.assignedObjective = order.assignment();
            directives.put(squad.id, directive(squad, home, home, switch (order.stage()) {
                case RESERVE -> AssignmentReason.DEFENDER_COUNTERATTACK_RESERVE;
                case PROBE -> AssignmentReason.DEFENDER_COUNTERATTACK_PROBE;
                case MUSTER -> AssignmentReason.DEFENDER_COUNTERATTACK_MUSTER;
                case LAUNCHED -> AssignmentReason.DEFENDER_COUNTERATTACK_LAUNCH;
            }));
        }
        sortiesToHandOff = List.copyOf(handoffs);
        if (handoffs.isEmpty()) return result.notes();
        List<String> notes = new ArrayList<>(result.notes());
        notes.add("counter-attack for " + counter.placeKey() + " draws garrisons out to sortie: squads " + handoffs);
        return notes;
    }

    /**
     * Garrisons that may sortie into a decided counter-attack (`conquest-command.md`, a place the defender loses): an
     * authored compound garrison of a compound this side still holds that is not home, out of local contact, with no
     * believed hostile mover within {@link #SORTIE_QUIET_CELLS} of it, within {@link #SORTIE_REACH_CELLS} of the place
     * the counter-attack stands for (of any turned place, while it stands for none), and above what its compound keeps
     * home (at least one squad, and half). Nothing is offered for a place that has not turned. Most of a Conquest
     * defender is garrisons; without this the counter-attack could raise only the mobile pool, which by the time a
     * place turns is in contact or dead, and a place was almost never retaken.
     */
    private Map<Integer, PlanningSquad> sortieGarrisons(ConquestCommandFrame frame,
                                                        List<PlaceCounterattack.Hostile> hostiles) {
        Map<Integer, PlanningSquad> out = new TreeMap<>();
        if (!PlaceCounterattack.garrisonSortie) return out;
        // The place the group stands for, or, with no group yet, every turned place it could form for.
        String key = counter.placeKey();
        List<PlaceCounterattack.Place> targets = new ArrayList<>();
        for (PlaceCounterattack.Place candidate : counterPlaces) {
            if (candidate.turned() && (key == null || candidate.key().equals(key))) targets.add(candidate);
        }
        if (targets.isEmpty()) return out;
        List<ConquestCommandFacts.Compound> compounds = frame.facts().compounds();
        Map<Integer, List<PlanningSquad>> byCompound = new TreeMap<>();
        for (CommandSquadState state : frame.squads()) {
            CommandDirective directive = state.directive();
            if (state.aliveMembers() <= 0 || state.machine() || state.crew() || directive == null
                    || directive.authority() != CommandAuthority.GARRISON
                    || !SETUP_GARRISON_ISSUER.equals(directive.issuer())) continue;
            int compound = garrisonCompound(state, compounds);
            if (compound < 0) continue;
            byCompound.computeIfAbsent(compound, c -> new ArrayList<>()).add(new PlanningSquad(state));
        }
        for (List<PlanningSquad> garrison : byCompound.values()) {
            int home = Math.max(1, (garrison.size() + 1) / 2);
            garrison.sort(Comparator.comparingInt((PlanningSquad s) -> -s.aliveMembers).thenComparingInt(s -> s.id));
            for (int i = home; i < garrison.size(); i++) {
                PlanningSquad squad = garrison.get(i);
                if (squad.localContact) continue;
                boolean inReach = false;
                for (PlaceCounterattack.Place place : targets) {
                    inReach |= Math.hypot(squad.anchorCellX - place.x(), squad.anchorCellY - place.y()) <= SORTIE_REACH_CELLS;
                }
                if (!inReach) continue;
                boolean quiet = true;
                for (PlaceCounterattack.Hostile hostile : hostiles) {
                    if (Math.hypot(hostile.x() - squad.anchorCellX, hostile.y() - squad.anchorCellY) <= SORTIE_QUIET_CELLS) {
                        quiet = false;
                        break;
                    }
                }
                if (quiet) out.put(squad.id, squad);
            }
        }
        return out;
    }

    /** The index of the held compound (never home) whose capture cell is nearest this garrison and within reach, or -1. */
    private static int garrisonCompound(CommandSquadState state, List<ConquestCommandFacts.Compound> compounds) {
        int best = -1;
        double bestGap = SORTIE_COMPOUND_CELLS;
        for (int i = 0; i < compounds.size(); i++) {
            ConquestCommandFacts.Compound compound = compounds.get(i);
            double gap = Math.hypot(compound.captureCellX() - state.anchorCellX(),
                    compound.captureCellY() - state.anchorCellY());
            if (gap <= bestGap) {
                best = i;
                bestGap = gap;
            }
        }
        if (best < 0) return -1;
        ConquestCommandFacts.Compound compound = compounds.get(best);
        if (compound.state() != CompoundService.CompoundState.DEFENDER_HELD
                || compound.node().kind == TacticalNode.Kind.COMMAND_POST) return -1;
        return best;
    }

    /** Whether a garrisonable belongs to the place the counter-attack stands for: its fort's belt, its compound's apron. */
    private boolean belongsTo(GarrisonableRegistry places, int place, String key) {
        if (key == null) return false;
        int fort = places.fortId(place);
        if (fort >= 0) return key.equals(FortPlaces.key(fort));
        int index = CompoundPlaces.index(key);
        if (index < 0 || index >= compoundsThisPlan.size()) return false;
        return CompoundGround.frontedBy(places, place, compoundsThisPlan) == compoundsThisPlan.get(index);
    }

    private static PlaceCounterattack.Body counterBody(PlanningSquad squad) {
        return new PlaceCounterattack.Body(squad.id, squad.anchorCellX, squad.anchorCellY, squad.aliveMembers,
                squad.originalMembers, squad.machine, squad.demolition, squad.localContact, squad.assignedObjective);
    }

    /** The keep's capture cell while this side still holds it, or {@code null}. */
    private static int[] keepCell(ConquestCommandFrame frame) {
        for (ConquestCommandFacts.Compound compound : frame.facts().compounds()) {
            if (compound.node().kind == TacticalNode.Kind.COMMAND_POST
                    && compound.state() != CompoundService.CompoundState.MARINE_HELD) {
                return new int[]{compound.captureCellX(), compound.captureCellY()};
            }
        }
        return null;
    }

    /**
     * Draws squads from the unselected reserve to ground-defence structures this side knows of (see
     * {@link DefenceStructureTactics}): its own when pressed, one taken from it, or an enemy one it has seen. The
     * response to a threatened track has already chosen, so structure work only ever spends the reserve. A squad left
     * holding an order for a structure that no longer needs it is released.
     */
    private List<String> assignDefenceStructureTasks(ConquestCommandFrame frame, List<PlanningSquad> candidates,
                                                     Set<Integer> selected, Map<Integer, SquadDirective> directives) {
        List<String> notes = new ArrayList<>();
        List<DefenceStructureTactics.Candidate> offered = new ArrayList<>();
        for (PlanningSquad squad : candidates) {
            if (selected.contains(squad.id) || squad.crew || squad.machine) continue;
            offered.add(new DefenceStructureTactics.Candidate(squad.id, squad.anchorCellX, squad.anchorCellY,
                    squad.originalDirective != null ? squad.originalDirective.assignment() : null,
                    squad.demolition));
        }
        Set<Integer> tasked = new HashSet<>();
        // A fort whose critical aspect is lost is the counter-attack's to retake, in proportion or not at all.
        List<DefenceStructureFact> work = PlaceCounterattack.isEnabled()
                ? FortPlaces.withheld(frame.perspective(), structures, fort -> counter.claimsGround(FortPlaces.key(fort)))
                : structures;
        for (DefenceStructureTactics.Task task : DefenceStructureTactics.plan(
                frame.perspective(), work, offered, frame.topology())) {
            PlanningSquad squad = candidateById(candidates, task.squadId());
            squad.assignedObjective = task.assignment();
            int home = homeTrack(squad);
            directives.put(squad.id, directive(squad, home, home, switch (task.role()) {
                case DEFEND -> AssignmentReason.DEFENCE_STRUCTURE_DEFEND;
                case RETAKE -> AssignmentReason.DEFENCE_STRUCTURE_RETAKE;
                case TAKE -> AssignmentReason.DEFENCE_STRUCTURE_TAKE;
            }));
            tasked.add(squad.id);
            selected.add(squad.id);
            notes.add("squad " + squad.id + " " + task.role() + (task.wreck() ? " (wreck) " : " ") + task.reason());
        }
        for (PlanningSquad squad : candidates) {
            if (tasked.contains(squad.id) || selected.contains(squad.id)) continue;
            if (DefenceStructureTactics.isTask(squad.assignedObjective, structures)) squad.assignedObjective = null;
        }
        return notes;
    }

    private static PlanningSquad candidateById(List<PlanningSquad> squads, int id) {
        for (PlanningSquad squad : squads) if (squad.id == id) return squad;
        throw new IllegalStateException("no candidate squad " + id);
    }

    private void clearMissionRally(PlanningSquad squad) {
        if (squad.assignedObjective != null
                && squad.assignedObjective.kind() == AssignmentKind.DEFEND_TRACK
                && !hasHigherAuthority(squad)) {
            squad.assignedObjective = null;
        }
    }

    private SquadDirective directive(PlanningSquad squad, int preferred, int effective,
                                     AssignmentReason reason) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        return new SquadDirective(squad.id, preferred, effective, reason,
                assignment != null ? assignment.kind() : null,
                assignment != null ? assignment.targetZoneId() : -1,
                assignment != null ? assignment.targetCellX() : -1,
                assignment != null ? assignment.targetCellY() : -1);
    }

    private int homeTrack(PlanningSquad squad) {
        return homeTracks.computeIfAbsent(squad.id,
                ignored -> trackFor(squad.centroidX, squad.centroidY));
    }

    private int trackFor(float centroidX, float centroidY) {
        int track = trackLayout.trackForLateral(trackLayout.lateralCoordinate(
                centroidX, centroidY));
        return Math.max(0, Math.min(trackLayout.trackCount() - 1, track));
    }

    private ConquestFrontSnapshot buildFrontSnapshot(
            ConquestCommandFrame frame,
            CommanderInfluenceSnapshot influence,
            Threat[] threats,
            int[] responderCaps,
            Map<Integer, SquadDirective> directives,
            Map<Integer, PlanningSquad> squads,
            Phase phase) {
        int tracks = trackLayout.trackCount();
        int[] preferredSquads = new int[tracks];
        int[] effectiveSquads = new int[tracks];
        int[] effectiveMembers = new int[tracks];
        int[] preferredMembers = new int[tracks];
        float[] bodyProgress = new float[tracks];
        float[] leadProgress = new float[tracks];
        Arrays.fill(leadProgress, -1f);

        for (PlanningSquad squad : squads.values()) {
            if (squad.aliveMembers <= 0) continue;
            SquadDirective directive = directives.get(squad.id);
            int preferred = directive != null ? directive.preferredTrack() : homeTrack(squad);
            int effective = directive != null ? directive.effectiveTrack() : preferred;
            float progress = trackLayout.assaultProgress(squad.centroidX, squad.centroidY);
            if (preferred >= 0 && preferred < tracks) {
                preferredSquads[preferred]++;
                preferredMembers[preferred] += squad.aliveMembers;
                bodyProgress[preferred] += progress * squad.aliveMembers;
                leadProgress[preferred] = Math.max(leadProgress[preferred], progress);
            }
            if (effective >= 0 && effective < tracks) {
                effectiveSquads[effective]++;
                effectiveMembers[effective] += squad.aliveMembers;
            }
        }

        List<TrackState> states = new ArrayList<>(tracks);
        for (int track = 0; track < tracks; track++) {
            float body = preferredMembers[track] > 0
                    ? bodyProgress[track] / preferredMembers[track] : -1f;
            Threat threat = threats[track];
            states.add(new TrackState(track,
                    trackLayout.lateralStartInclusive(track),
                    trackLayout.lateralEndInclusive(track),
                    preferredSquads[track], effectiveSquads[track],
                    effectiveMembers[track], body, leadProgress[track],
                    threat.knownHostileFront, threat.contacts,
                    threat.friendlyPressure, threat.hostilePressure, -1,
                    responderCaps[track]));
        }

        int remainingCompounds = 0;
        int keepZone = -1;
        CompoundService.CompoundState keepState = null;
        for (ConquestCommandFacts.Compound compound : frame.facts().compounds()) {
            if (compound.state() != CompoundService.CompoundState.MARINE_HELD) {
                remainingCompounds++;
            }
            if (compound.node().kind == TacticalNode.Kind.COMMAND_POST) {
                keepZone = compound.captureZoneId();
                keepState = compound.state();
            }
        }
        return new ConquestFrontSnapshot(frame.tick(),
                influence != null ? influence.updatedTick() : -1,
                Faction.DEFENDER, trackLayout.axis(), phase,
                remainingCompounds, keepZone, keepState,
                states, squadStates(squads, directives),
                new ArrayList<>(directives.values()));
    }

    private static List<ConquestFrontSnapshot.SquadState> squadStates(
            Map<Integer, PlanningSquad> squads,
            Map<Integer, SquadDirective> directives) {
        List<ConquestFrontSnapshot.SquadState> states = new ArrayList<>(squads.size());
        for (PlanningSquad squad : squads.values()) {
            SquadDirective directive = directives.get(squad.id);
            int targetZone = directive != null ? directive.targetZoneId() : -1;
            int membersInTargetZone = 0;
            if (targetZone >= 0) {
                for (int zoneId : squad.memberZoneIds) {
                    if (zoneId == targetZone) membersInTargetZone++;
                }
            }
            states.add(new ConquestFrontSnapshot.SquadState(
                    squad.id, squad.aliveMembers, squad.centroidX,
                    squad.centroidY, squad.currentZoneId,
                    squad.executionSuspension, squad.localContact,
                    squad.activePathMembers, membersInTargetZone, 0,
                    squad.underFireRecently, squad.moraleBroken,
                    squad.currentGoal, squad.currentAction,
                    squad.movingMembers, squad.coveredFromPrimaryMembers,
                    squad.primaryEngageableMembers,
                    squad.primaryEngageableFireTeams,
                    squad.contactPosture, squad.contactDoctrine,
                    squad.contactInitiative, squad.coolingDownMembers));
        }
        return states;
    }

    private List<CommandProposal> buildProposals(
            ConquestCommandFrame frame,
            Map<Integer, PlanningSquad> squads,
            Map<Integer, SquadDirective> directives,
            Threat[] threats) {
        List<CommandProposal> proposals = new ArrayList<>();
        for (Map.Entry<Integer, SquadDirective> entry : directives.entrySet()) {
            int squadId = entry.getKey();
            PlanningSquad planned = squads.get(squadId);
            CommandSquadState frozen = frame.squad(squadId);
            if (planned == null || frozen == null) continue;
            String reason = entry.getValue().reason().name();
            CommandStabilityBreak stabilityBreak = stabilityBreak(
                    frame, planned, frozen.directive(), entry.getValue().reason(),
                    threats);
            if (hasHigherAuthority(planned)
                    || planned.assignedObjective != null
                    && planned.assignedObjective.kind() != AssignmentKind.DEFEND_TRACK
                    && !planned.assignedObjective.mansPlace()
                    && !isStagePost(planned)
                    && !counter.claims(squadId)
                    && !isInsideFallBack(planned)
                    && !DefenceStructureTactics.isTask(planned.assignedObjective, structures)) {
                proposals.add(CommandProposal.retain(squadId,
                        CommandAuthority.MISSION_COMMAND, reason));
            } else if (planned.assignedObjective != null) {
                proposals.add(CommandProposal.assign(planned.assignedObjective,
                        CommandAuthority.MISSION_COMMAND, reason, stabilityBreak));
            } else if (frozen.assignment() != null) {
                // A squad the counter-attack let go is released now: the post it held is gone, not merely superseded.
                CommandStabilityBreak release = counterLeft.containsKey(squadId)
                        ? CommandStabilityBreak.CONTEXT_INVALIDATED : stabilityBreak;
                proposals.add(CommandProposal.release(squadId,
                        CommandAuthority.MISSION_COMMAND, reason, release));
            } else {
                proposals.add(CommandProposal.retain(squadId,
                        CommandAuthority.MISSION_COMMAND, reason));
            }
        }
        return proposals;
    }

    private CommandStabilityBreak stabilityBreak(
            ConquestCommandFrame frame, PlanningSquad squad,
            CommandDirective incumbent, AssignmentReason reason,
            Threat[] threats) {
        if (incumbent == null || incumbent.assignment() == null
                || !strategyId().equals(incumbent.issuer())
                || Objects.equals(incumbent.assignment(), squad.assignedObjective)) {
            return CommandStabilityBreak.NONE;
        }
        ObjectiveAssignment old = incumbent.assignment();
        if (old.kind() != AssignmentKind.DEFEND_TRACK) {
            return CommandStabilityBreak.NONE;
        }
        if (reason == AssignmentReason.DEFENDER_LOCAL_CONTACT) {
            return CommandStabilityBreak.CONTEXT_INVALIDATED;
        }
        if (!frame.topology().inBounds(old.targetCellX(), old.targetCellY())
                || !frame.topology().isWalkable(
                old.targetCellX(), old.targetCellY())
                || !frame.topology().reachable(squad.anchorCellX,
                squad.anchorCellY, old.targetCellX(), old.targetCellY())) {
            return CommandStabilityBreak.TARGET_UNREACHABLE;
        }
        SquadDirective prior = frontSnapshot != null
                ? frontSnapshot.directiveFor(squad.id) : null;
        int priorTrack = prior != null ? prior.effectiveTrack()
                : trackLayout.trackForCell(
                old.targetCellX(), old.targetCellY());
        if (priorTrack < 0 || priorTrack >= threats.length
                || !threats[priorTrack].active()) {
            return CommandStabilityBreak.CONTEXT_INVALIDATED;
        }
        return CommandStabilityBreak.NONE;
    }
}
