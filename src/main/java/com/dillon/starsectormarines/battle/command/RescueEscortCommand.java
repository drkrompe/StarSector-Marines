package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RescueCommandFacts.AuthoredDuty;
import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.command.RescueCommandSnapshot.HivePicture;
import com.dillon.starsectormarines.battle.command.RescueCommandSnapshot.Role;
import com.dillon.starsectormarines.battle.command.RescueCommandSnapshot.SquadIntent;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.evacuation.CivilianRedoubt;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.sim.BattleView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Frame-only Marine commander for Civilian Rescue. Authored shelter and pickup
 * guards remain external while the mobile force relieves the shelter and then
 * occupies stable, separated cohort-screen roles along the public route.
 *
 * <p>On Colony Breach it also answers the hive posture frozen into it at
 * launch: a {@link StrikeDetachment} taken from the squads free of escort duty
 * goes against a bore while the {@link ReadinessGate} holds open, ordered at
 * the bore's cell as at any structure, and comes back when it closes.
 */
public final class RescueEscortCommand implements AutonomousMissionCommand<
        RescueCommandFrame, RescueCommandSnapshot> {

    public static final int ADVANCE_SCREEN_CELLS = 5;
    /**
     * Route cells the close escort's rally anchor leads a cohort of one by. The
     * people follow their squad and stand once beside it ({@code
     * CivilianEvacuationSystem}, two cells), so the escort leads by more than that
     * or the cohort stands round it and nobody moves again ({@link #escortLead}).
     */
    static final int ESCORT_LEAD_CELLS = 4;
    /** The most route cells the close escort leads by, however many walk behind it. */
    static final int MAX_ESCORT_LEAD_CELLS = 10;
    /** Compatibility name for the legacy two-cell pressured bound. */
    public static final int ENGAGED_BOUND_CELLS = 2;
    public static final int ENGAGED_BOUND_TICKS = 150;
    static final int LATERAL_SLOT_SPACING = 5;
    static final int ECHELON_DEPTH_SPACING = 3;
    static final int MIN_SLOT_SEPARATION = 4;
    private static final int SLOT_SEARCH_RADIUS = 5;
    private static final List<Role> SCREEN_ROLES = List.of(
            Role.LEAD_SCREEN, Role.LEFT_SCREEN,
            Role.RIGHT_SCREEN, Role.REAR_SCREEN);

    private final CivilianEvacuationPlacement placement;
    private final Map<Integer, Role> roles = new HashMap<>();
    private final Map<Integer, Integer> nextPressureBoundTick = new HashMap<>();
    private volatile RescueCommandSnapshot rescueSnapshot;
    /** Several redoubts: each walking group's close escort, by redoubt number. */
    private final Map<Integer, Integer> escortOf = new HashMap<>();
    /** Several redoubts: the one the mobile force is relieving, or {@code -1}. */
    private int reliefTarget = -1;
    private final StrikeDetachment strike;

    public RescueEscortCommand(CivilianEvacuationPlacement placement) {
        this(placement, HivePosture.DEFAULT);
    }

    /** @param posture the hive posture chosen at the briefing; {@code null} is the default */
    public RescueEscortCommand(CivilianEvacuationPlacement placement, HivePosture posture) {
        this.placement = Objects.requireNonNull(placement, "placement");
        this.strike = new StrikeDetachment(posture);
    }

    public HivePosture posture() { return strike.posture(); }

    public RescueCommandSnapshot rescueSnapshot() { return rescueSnapshot; }

    /** Direct-test seam; production registration uses the paired command service. */
    public void tick(BattleView sim) {
        CommanderService.runSingle(this, RescueCommandDisclosure.INSTANCE, sim);
    }

    @Override public Faction faction() { return Faction.MARINE; }
    @Override public String strategyId() { return "rescue-corridor"; }

    @Override
    public CommandPlan<RescueCommandSnapshot> plan(RescueCommandFrame frame) {
        if (frame.facts().redoubts().size() > 1) return planRedoubts(frame);
        ExtractionObjectiveFacts cohort = frame.facts().cohort();
        List<CommandSquadState> pool = frame.squads().stream()
                .filter(squad -> squad.aliveMembers() > 0)
                .filter(squad -> frame.facts().authoredDuty(squad.squadId()) == null)
                .filter(squad -> squad.directive() == null
                        || squad.directive().authority().priority()
                        <= CommandAuthority.MISSION_COMMAND.priority())
                .toList();
        Set<Integer> poolIds = new HashSet<>();
        for (CommandSquadState squad : pool) poolIds.add(squad.squadId());
        roles.keySet().retainAll(poolIds);
        nextPressureBoundTick.keySet().retainAll(poolIds);
        strike.observeLift(frame.tick(), frame.squads(), frame.facts().emplacements(), placement.liftX, placement.liftY);
        strike.observeCohort(frame.tick(), cohort.lostElements());
        List<CommandSquadState> screens = pool;
        if (!terminal(cohort)) {
            screens = pool.stream().filter(s -> !strike.members().contains(s.squadId())).toList();
            assignStableRoles(screens);
            List<CommandSquadState> candidates = new ArrayList<>();
            for (CommandSquadState squad : pool) {
                if (roles.get(squad.squadId()) != Role.COHORT_ESCORT
                        || strike.members().contains(squad.squadId())) candidates.add(squad);
            }
            int[] walkFrom = "AT_SOURCE".equals(cohort.phase())
                    ? new int[]{placement.shelterApproachX, placement.shelterApproachY}
                    : new int[]{validCell(cohort.payloadCellX(), placement.shelterX),
                            validCell(cohort.payloadCellY(), placement.shelterY)};
            updateStrike(frame, candidates, pool.size(), ReadinessGate.escortNeed(List.of()), List.of(walkFrom));
            screens = pool.stream().filter(s -> !strike.members().contains(s.squadId())).toList();
        }

        boolean atShelter = "AT_SOURCE".equals(cohort.phase());
        int anchorX = atShelter ? placement.shelterApproachX
                : validCell(cohort.payloadCellX(), placement.shelterX);
        int anchorY = atShelter ? placement.shelterApproachY
                : validCell(cohort.payloadCellY(), placement.shelterY);
        // The cohort's cell is its centroid, which can fall on a wall between two
        // of its members; a route from there is empty and the guide would sit on
        // the cohort itself, so no screen ever leads it on. Route from the
        // nearest open cell instead.
        if (!atShelter) {
            int[] open = nearestWalkable(frame.topology(), anchorX, anchorY);
            if (open != null) {
                anchorX = open[0];
                anchorY = open[1];
            }
        }
        int[] route = frame.topology().route(anchorX, anchorY,
                placement.liftX, placement.liftY);
        int lead = escortLead(cohort.activeElements());
        int guideIndex = Paths.isEmpty(route) ? -1
                : Math.min(Math.max(ADVANCE_SCREEN_CELLS, lead + 1),
                Paths.cellCount(route) - 1);
        int guideX = guideIndex >= 0 ? Paths.cellX(route, guideIndex) : anchorX;
        int guideY = guideIndex >= 0 ? Paths.cellY(route, guideIndex) : anchorY;
        int[] forward = direction(anchorX, anchorY, guideX, guideY);
        int escortIndex = Paths.isEmpty(route) ? -1
                : Math.min(lead, Paths.cellCount(route) - 1);
        int escortX = escortIndex >= 0 ? Paths.cellX(route, escortIndex) : anchorX;
        int escortY = escortIndex >= 0 ? Paths.cellY(route, escortIndex) : anchorY;

        List<CommandProposal> proposals = new ArrayList<>();
        List<SquadIntent> intents = new ArrayList<>();
        List<int[]> claimed = new ArrayList<>();
        int knownPressure = frame.influence() != null
                ? frame.influence().contacts().size() : 0;
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            if (!poolIds.contains(squad.squadId())) {
                intents.add(externalIntent(squad,
                        frame.facts().authoredDuty(squad.squadId())));
                continue;
            }
            if (terminal(cohort)) {
                proposals.add(releaseOrRetain(squad, "RESCUE_TERMINAL"));
                intents.add(new SquadIntent(squad.squadId(), Role.RELEASED,
                        "RESCUE_TERMINAL", null, -1, -1,
                        squad.localContact(), false));
                continue;
            }

            if (strike.members().contains(squad.squadId())) {
                strikeOrder(squad, proposals, intents);
                continue;
            }
            Role role = roles.get(squad.squadId());
            int roleOrdinal = roleOrdinal(squad, screens, role);
            int[] ideal = atShelter
                    ? formationCell(role, roleOrdinal,
                    anchorX, anchorY, forward)
                    : corridorCell(role, escortX, escortY, guideX, guideY,
                    forward, roleOrdinal);
            int[] target = nearestReachable(squad, ideal[0], ideal[1],
                    anchorX, anchorY, frame.topology(), claimed);
            if (target == null) {
                proposals.add(releaseOrRetain(squad,
                        "RESCUE_TARGET_UNREACHABLE"));
                intents.add(new SquadIntent(squad.squadId(), Role.STRANDED,
                        "RESCUE_TARGET_UNREACHABLE", null, -1, -1,
                        squad.localContact(), false));
                continue;
            }

            boolean slowed = locallySlow(squad, frame.tick(), target);
            if (slowed) {
                ObjectiveAssignment incumbent = squad.directive().assignment();
                target = new int[]{incumbent.targetCellX(),
                        incumbent.targetCellY()};
            }
            claimed.add(target);
            ObjectiveAssignment assignment = ObjectiveAssignment.escort(
                    squad.squadId(), target[0], target[1]);
            String reason = reason(role, atShelter, slowed);
            proposals.add(CommandProposal.assign(assignment,
                    CommandAuthority.MISSION_COMMAND, reason,
                    stabilityBreak(squad, assignment)));
            intents.add(new SquadIntent(squad.squadId(), role, reason,
                    assignment.kind(), target[0], target[1],
                    squad.localContact(), slowed));
        }

        RescueCommandSnapshot detail = new RescueCommandSnapshot(
                frame.tick(), faction(), cohort.phase(), cohort.payloadId(),
                cohort.payloadName(), placement.shelterX, placement.shelterY,
                cohort.payloadCellX(), cohort.payloadCellY(), guideX, guideY,
                placement.liftX, placement.liftY, cohort.initialElements(),
                cohort.activeElements(), cohort.boardedElements(),
                cohort.lostElements(), cohort.progress(),
                cohort.escortPresent(), cohort.controllingSquadId(),
                knownPressure, cohort.complete(), cohort.failed(),
                cohort.failure(), intents, hivePicture());
        return new CommandPlan<>(faction(), strategyId(), cohort.phase(),
                frame.tick(), frame.influence() != null
                ? frame.influence().updatedTick() : -1,
                pool.size(), 0, List.of(summary(cohort) + strikeSummary()), proposals, detail);
    }

    /**
     * The cohort is holed up in several redoubts ({@code extraction-command.md},
     * Several redoubts). Every redoubt whose people are walking out gets its
     * own close escort, the free squad nearest them, kept until they are
     * aboard. The rest of the force relieves one pending redoubt at a time,
     * the one its route from where it stands is shortest to, and keeps that
     * choice until the redoubt is reached or falls. Escorts come before
     * relief: people in the open are exposed, people behind a door are not.
     * With nothing left to relieve the force screens the largest walking
     * group, and with nobody walking it holds at the lift.
     */
    private CommandPlan<RescueCommandSnapshot> planRedoubts(RescueCommandFrame frame) {
        ExtractionObjectiveFacts cohort = frame.facts().cohort();
        List<CivilianRedoubt> redoubts = frame.facts().redoubts();
        CommandTopology topology = frame.topology();
        List<CommandSquadState> pool = frame.squads().stream()
                .filter(squad -> squad.aliveMembers() > 0)
                .filter(squad -> frame.facts().authoredDuty(squad.squadId()) == null)
                .filter(squad -> squad.directive() == null
                        || squad.directive().authority().priority()
                        <= CommandAuthority.MISSION_COMMAND.priority())
                .toList();
        Set<Integer> poolIds = new HashSet<>();
        for (CommandSquadState squad : pool) poolIds.add(squad.squadId());
        roles.keySet().retainAll(poolIds);
        nextPressureBoundTick.keySet().retainAll(poolIds);
        boolean terminal = terminal(cohort);

        escortOf.entrySet().removeIf(entry -> !poolIds.contains(entry.getValue())
                || walkers(redoubts, entry.getKey()) == null);
        strike.observeLift(frame.tick(), frame.squads(), frame.facts().emplacements(), placement.liftX, placement.liftY);
        strike.observeCohort(frame.tick(), cohort.lostElements());
        if (!terminal) {
            // The strike is decided before new escorts are drawn, from the squads
            // not already escorting; a squad on strike is not drawn as an escort.
            List<CommandSquadState> candidates = pool.stream()
                    .filter(s -> !escortOf.containsValue(s.squadId())).toList();
            List<int[]> walkFrom = new ArrayList<>();
            for (CivilianRedoubt redoubt : redoubts) {
                if (redoubt.walking() > 0) walkFrom.add(new int[]{redoubt.walkingCellX(), redoubt.walkingCellY()});
                else if (redoubt.pending()) walkFrom.add(new int[]{redoubt.approachX(), redoubt.approachY()});
            }
            updateStrike(frame, candidates, pool.size(), ReadinessGate.escortNeed(redoubts), walkFrom);
        }
        if (!terminal) {
            for (CivilianRedoubt redoubt : redoubts) {
                if (redoubt.walking() == 0 || escortOf.containsKey(redoubt.number())) continue;
                CommandSquadState nearest = null;
                int best = Integer.MAX_VALUE;
                for (CommandSquadState squad : pool) {
                    if (escortOf.containsValue(squad.squadId()) || strike.members().contains(squad.squadId())) continue;
                    int distance = Math.abs(squad.anchorCellX() - redoubt.walkingCellX())
                            + Math.abs(squad.anchorCellY() - redoubt.walkingCellY());
                    if (distance < best) {
                        best = distance;
                        nearest = squad;
                    }
                }
                if (nearest != null) escortOf.put(redoubt.number(), nearest.squadId());
            }
        }
        Map<Integer, Integer> escorting = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : escortOf.entrySet()) escorting.put(entry.getValue(), entry.getKey());
        List<CommandSquadState> force = pool.stream().filter(s -> !escorting.containsKey(s.squadId())
                && !strike.members().contains(s.squadId())).toList();
        assignScreenRoles(force);

        CivilianRedoubt relief = redoubt(redoubts, reliefTarget);
        if (relief == null || !relief.pending()) {
            reliefTarget = chooseRelief(force, redoubts, topology);
            relief = redoubt(redoubts, reliefTarget);
        }
        CivilianRedoubt screened = null;
        for (CivilianRedoubt redoubt : redoubts) {
            if (redoubt.walking() > 0 && (screened == null || redoubt.walking() > screened.walking())) {
                screened = redoubt;
            }
        }

        // The force's anchor: the door it relieves, else the walkers it screens, else the lift.
        int anchorX, anchorY;
        if (relief != null) {
            anchorX = relief.approachX();
            anchorY = relief.approachY();
        } else if (screened != null) {
            int[] open = nearestWalkable(topology, screened.walkingCellX(), screened.walkingCellY());
            anchorX = open != null ? open[0] : screened.walkingCellX();
            anchorY = open != null ? open[1] : screened.walkingCellY();
        } else {
            anchorX = placement.liftX;
            anchorY = placement.liftY;
        }
        int[] route = topology.route(anchorX, anchorY, placement.liftX, placement.liftY);
        int screenedLead = escortLead(screened != null ? screened.walking() : 0);
        int guideIndex = Paths.isEmpty(route) ? -1
                : Math.min(Math.max(ADVANCE_SCREEN_CELLS, screenedLead + 1), Paths.cellCount(route) - 1);
        int guideX = guideIndex >= 0 ? Paths.cellX(route, guideIndex) : anchorX;
        int guideY = guideIndex >= 0 ? Paths.cellY(route, guideIndex) : anchorY;
        int[] forward = direction(anchorX, anchorY, guideX, guideY);
        int escortIndex = Paths.isEmpty(route) ? -1 : Math.min(screenedLead, Paths.cellCount(route) - 1);
        int forceEscortX = escortIndex >= 0 ? Paths.cellX(route, escortIndex) : anchorX;
        int forceEscortY = escortIndex >= 0 ? Paths.cellY(route, escortIndex) : anchorY;

        List<CommandProposal> proposals = new ArrayList<>();
        List<SquadIntent> intents = new ArrayList<>();
        List<int[]> claimed = new ArrayList<>();
        int knownPressure = frame.influence() != null ? frame.influence().contacts().size() : 0;
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            if (!poolIds.contains(squad.squadId())) {
                intents.add(externalIntent(squad, frame.facts().authoredDuty(squad.squadId())));
                continue;
            }
            if (terminal) {
                proposals.add(releaseOrRetain(squad, "RESCUE_TERMINAL"));
                intents.add(new SquadIntent(squad.squadId(), Role.RELEASED, "RESCUE_TERMINAL", null, -1, -1,
                        squad.localContact(), false));
                continue;
            }
            if (strike.members().contains(squad.squadId())) {
                strikeOrder(squad, proposals, intents);
                continue;
            }
            Role role;
            String reason;
            int[] ideal;
            int fallbackX, fallbackY;
            Integer escorted = escorting.get(squad.squadId());
            if (escorted != null) {
                CivilianRedoubt group = redoubt(redoubts, escorted);
                int[] open = nearestWalkable(topology, group.walkingCellX(), group.walkingCellY());
                fallbackX = open != null ? open[0] : group.walkingCellX();
                fallbackY = open != null ? open[1] : group.walkingCellY();
                int[] walk = topology.route(fallbackX, fallbackY, placement.liftX, placement.liftY);
                int lead = Paths.isEmpty(walk) ? -1
                        : Math.min(escortLead(group.walking()), Paths.cellCount(walk) - 1);
                ideal = lead >= 0 ? new int[]{Paths.cellX(walk, lead), Paths.cellY(walk, lead)}
                        : new int[]{fallbackX, fallbackY};
                role = Role.COHORT_ESCORT;
                reason = "REDOUBT_ESCORT_" + escorted;
            } else {
                role = roles.get(squad.squadId());
                int ordinal = roleOrdinal(squad, force, role);
                fallbackX = anchorX;
                fallbackY = anchorY;
                if (relief != null) {
                    ideal = formationCell(role, ordinal, anchorX, anchorY, forward);
                    reason = "REDOUBT_RELIEF_" + relief.number();
                } else if (screened != null) {
                    ideal = corridorCell(role, forceEscortX, forceEscortY, guideX, guideY, forward, ordinal);
                    reason = "MOVING_" + role.name();
                } else {
                    ideal = formationCell(role, ordinal, anchorX, anchorY, forward);
                    reason = "LIFT_HOLD";
                }
            }
            int[] target = nearestReachable(squad, ideal[0], ideal[1], fallbackX, fallbackY, topology, claimed);
            if (target == null) {
                proposals.add(releaseOrRetain(squad, "RESCUE_TARGET_UNREACHABLE"));
                intents.add(new SquadIntent(squad.squadId(), Role.STRANDED, "RESCUE_TARGET_UNREACHABLE", null,
                        -1, -1, squad.localContact(), false));
                continue;
            }
            boolean slowed = locallySlow(squad, frame.tick(), target);
            if (slowed) {
                ObjectiveAssignment incumbent = squad.directive().assignment();
                target = new int[]{incumbent.targetCellX(), incumbent.targetCellY()};
                reason = reason + "_LOCAL_PRESSURE_HOLD";
            }
            claimed.add(target);
            ObjectiveAssignment assignment = ObjectiveAssignment.escort(squad.squadId(), target[0], target[1]);
            proposals.add(CommandProposal.assign(assignment, CommandAuthority.MISSION_COMMAND, reason,
                    stabilityBreak(squad, assignment)));
            intents.add(new SquadIntent(squad.squadId(), role, reason, assignment.kind(), target[0], target[1],
                    squad.localContact(), slowed));
        }

        RescueCommandSnapshot detail = new RescueCommandSnapshot(
                frame.tick(), faction(), cohort.phase(), cohort.payloadId(),
                cohort.payloadName(), placement.shelterX, placement.shelterY,
                cohort.payloadCellX(), cohort.payloadCellY(), guideX, guideY,
                placement.liftX, placement.liftY, cohort.initialElements(),
                cohort.activeElements(), cohort.boardedElements(),
                cohort.lostElements(), cohort.progress(),
                cohort.escortPresent(), cohort.controllingSquadId(),
                knownPressure, cohort.complete(), cohort.failed(),
                cohort.failure(), intents, hivePicture());
        return new CommandPlan<>(faction(), strategyId(), cohort.phase(),
                frame.tick(), frame.influence() != null ? frame.influence().updatedTick() : -1,
                pool.size(), 0, List.of(summary(cohort) + " relief=" + reliefTarget
                        + " escorts=" + escortOf.size() + strikeSummary()), proposals, detail);
    }

    /**
     * Route cells the close escort leads {@code walkers} by from their anchor, their mean cell: past the two at which
     * the people stand beside it by half the column they make, since in a lane the front of a long column is beside
     * the escort while its middle, the anchor, is still well back, and an escort that led the middle by less would
     * stand there with the people beside it. At most {@link #MAX_ESCORT_LEAD_CELLS}.
     */
    static int escortLead(int walkers) {
        return Math.min(MAX_ESCORT_LEAD_CELLS, ESCORT_LEAD_CELLS + Math.max(0, walkers) / 2);
    }

    /**
     * Reads the readiness gate and moves the detachment. {@code walkFrom} are
     * the places people will walk to the lift from (walking groups, doors
     * still to relieve); a bore near any of those walks is on the route.
     */
    private void updateStrike(RescueCommandFrame frame, List<CommandSquadState> candidates, int mobileForce,
                              int escortNeed, List<int[]> walkFrom) {
        List<KnownBore> bores = frame.facts().bores();
        BiPredicate<Integer, Integer> onRoute = onRoute(frame.topology(), bores, walkFrom);
        strike.update(frame.tick(), bores, frame.facts().emplacements(), placement.liftX, placement.liftY,
                candidates, mobileForce, escortNeed, onRoute);
    }

    /**
     * Whether a cell is within {@link StrikeDetachment#ON_ROUTE_CELLS} of a
     * route from one of {@code walkFrom} to the lift. Routes are asked for
     * only when a living bore exists and only for Evacuate, the one posture
     * that reads them; every other posture gets a predicate that is never
     * consulted.
     */
    private BiPredicate<Integer, Integer> onRoute(CommandTopology topology, List<KnownBore> bores,
                                                  List<int[]> walkFrom) {
        if (strike.posture() != HivePosture.EVACUATE || bores.stream().noneMatch(KnownBore::living)) {
            return (x, y) -> false;
        }
        List<int[]> routes = new ArrayList<>();
        for (int[] from : walkFrom) {
            int[] open = nearestWalkable(topology, from[0], from[1]);
            if (open == null) continue;
            int[] route = topology.route(open[0], open[1], placement.liftX, placement.liftY);
            if (!Paths.isEmpty(route)) routes.add(route);
        }
        return (x, y) -> {
            for (int[] route : routes) {
                for (int i = 0, n = Paths.cellCount(route); i < n; i++) {
                    if (Math.max(Math.abs(Paths.cellX(route, i) - x), Math.abs(Paths.cellY(route, i) - y))
                            <= StrikeDetachment.ON_ROUTE_CELLS) return true;
                }
            }
            return false;
        };
    }

    /** A detachment squad's order: attack-move onto the bore with its cell named as the thing to open. */
    private void strikeOrder(CommandSquadState squad, List<CommandProposal> proposals, List<SquadIntent> intents) {
        KnownBore bore = strike.target();
        ObjectiveAssignment assignment = ObjectiveAssignment.attackMove(squad.squadId(), bore.x(), bore.y())
                .withBreach(bore.x(), bore.y());
        String reason = "STRIKE_BORE_" + bore.key();
        proposals.add(CommandProposal.assign(assignment, CommandAuthority.MISSION_COMMAND, reason,
                stabilityBreak(squad, assignment)));
        intents.add(new SquadIntent(squad.squadId(), Role.STRIKE_DETACHMENT, reason, assignment.kind(),
                bore.x(), bore.y(), squad.localContact(), false));
    }

    private HivePicture hivePicture() {
        KnownBore target = strike.target();
        ReadinessGate gate = strike.gate();
        return new HivePicture(strike.posture(),
                gate == null ? null : StrikeDetachment.gateLine(gate, strike.inputs()),
                target == null ? null : target.key(), target == null ? -1 : target.x(),
                target == null ? -1 : target.y(), List.copyOf(strike.members()), strike.decisions());
    }

    private String strikeSummary() {
        KnownBore target = strike.target();
        return target == null ? "" : " strike=" + target.key() + strike.members();
    }

    /** Screen roles for the squads not escorting, spread evenly; an escort's old role gives way. */
    private void assignScreenRoles(List<CommandSquadState> force) {
        Set<Integer> ids = new HashSet<>();
        for (CommandSquadState squad : force) ids.add(squad.squadId());
        roles.keySet().retainAll(ids);
        roles.values().removeIf(role -> role == Role.COHORT_ESCORT);
        Map<Role, Integer> loads = new EnumMap<>(Role.class);
        for (Role role : roles.values()) loads.merge(role, 1, Integer::sum);
        for (CommandSquadState squad : force) {
            if (roles.containsKey(squad.squadId())) continue;
            Role selected = SCREEN_ROLES.stream()
                    .min(Comparator.comparingInt((Role role) -> loads.getOrDefault(role, 0))
                            .thenComparingInt(Enum::ordinal))
                    .orElse(Role.LEAD_SCREEN);
            roles.put(squad.squadId(), selected);
            loads.merge(selected, 1, Integer::sum);
        }
        // The lead screen stands on the door being relieved. When its squad left to escort a
        // redoubt's people, the others kept their flank and rear slots and nobody reached the
        // door: the squad on the most crowded slot, the rearmost on a tie, steps up.
        if (!force.isEmpty() && !loads.containsKey(Role.LEAD_SCREEN)) {
            CommandSquadState step = null;
            for (CommandSquadState squad : force) {
                if (step == null || slotOrder(roles.get(squad.squadId()), loads)
                        > slotOrder(roles.get(step.squadId()), loads)) step = squad;
            }
            roles.put(step.squadId(), Role.LEAD_SCREEN);
        }
    }

    private static int slotOrder(Role role, Map<Role, Integer> loads) {
        return loads.getOrDefault(role, 0) * SCREEN_ROLES.size() + SCREEN_ROLES.indexOf(role);
    }

    /**
     * The pending redoubt nearest the force by route from where it stands
     * (the mean of its squads' cells), the lower number on a tie; a redoubt
     * with no route is taken by straight distance only when none has one.
     */
    static int chooseRelief(List<CommandSquadState> force, List<CivilianRedoubt> redoubts,
                            CommandTopology topology) {
        if (force.isEmpty()) return -1;
        int sumX = 0, sumY = 0;
        for (CommandSquadState squad : force) {
            sumX += squad.anchorCellX();
            sumY += squad.anchorCellY();
        }
        int fromX = sumX / force.size(), fromY = sumY / force.size();
        int[] open = nearestWalkable(topology, fromX, fromY);
        if (open != null) {
            fromX = open[0];
            fromY = open[1];
        }
        int best = -1;
        long bestCost = Long.MAX_VALUE;
        for (CivilianRedoubt redoubt : redoubts) {
            if (!redoubt.pending()) continue;
            int[] route = topology.route(fromX, fromY, redoubt.approachX(), redoubt.approachY());
            int straight = Math.abs(redoubt.approachX() - fromX) + Math.abs(redoubt.approachY() - fromY);
            long cost = Paths.isEmpty(route) ? (1L << 32) + straight : Paths.cellCount(route);
            if (cost < bestCost) {
                bestCost = cost;
                best = redoubt.number();
            }
        }
        return best;
    }

    private static CivilianRedoubt redoubt(List<CivilianRedoubt> redoubts, int number) {
        for (CivilianRedoubt redoubt : redoubts) if (redoubt.number() == number) return redoubt;
        return null;
    }

    private static CivilianRedoubt walkers(List<CivilianRedoubt> redoubts, int number) {
        CivilianRedoubt redoubt = redoubt(redoubts, number);
        return redoubt != null && redoubt.walking() > 0 ? redoubt : null;
    }

    @Override
    public void publish(CommanderSnapshot<RescueCommandSnapshot> snapshot) {
        rescueSnapshot = snapshot.detail();
    }

    private void assignStableRoles(List<CommandSquadState> pool) {
        restoreEscortRole(pool.stream().map(CommandSquadState::squadId).toList(), roles);
        Map<Role, Integer> loads = new EnumMap<>(Role.class);
        for (Role role : roles.values()) loads.merge(role, 1, Integer::sum);
        for (CommandSquadState squad : pool) {
            if (roles.containsKey(squad.squadId())) continue;
            Role selected = SCREEN_ROLES.stream()
                    .min(Comparator
                            .comparingInt((Role role) ->
                                    loads.getOrDefault(role, 0))
                            .thenComparingInt(Enum::ordinal))
                    .orElse(Role.LEAD_SCREEN);
            roles.put(squad.squadId(), selected);
            loads.merge(selected, 1, Integer::sum);
        }
    }

    /** Loss or an external player duty may remove the escort while every survivor already has a screen role. */
    static void restoreEscortRole(List<Integer> eligible, Map<Integer, Role> roles) {
        roles.keySet().retainAll(eligible);
        if (!roles.containsValue(Role.COHORT_ESCORT)) eligible.stream().min(Integer::compareTo)
                .ifPresent(id -> roles.put(id, Role.COHORT_ESCORT));
    }

    private boolean locallySlow(CommandSquadState squad, int tick,
                                int[] desired) {
        if (!squad.localContact() || squad.directive() == null
                || !strategyId().equals(squad.directive().issuer())
                || squad.directive().assignment() == null) {
            nextPressureBoundTick.remove(squad.squadId());
            return false;
        }
        int next = nextPressureBoundTick.computeIfAbsent(squad.squadId(),
                ignored -> tick + ENGAGED_BOUND_TICKS);
        if (tick < next) return true;
        nextPressureBoundTick.put(squad.squadId(),
                tick + ENGAGED_BOUND_TICKS);
        ObjectiveAssignment current = squad.directive().assignment();
        return current.targetCellX() == desired[0]
                && current.targetCellY() == desired[1];
    }

    private static int[] corridorCell(Role role, int escortX, int escortY,
                                      int guideX, int guideY, int[] forward,
                                      int roleOrdinal) {
        if (role == Role.COHORT_ESCORT) return new int[]{escortX, escortY};
        return formationCell(role, roleOrdinal, guideX, guideY, forward);
    }

    private static int[] formationCell(Role role, int roleOrdinal,
                                       int anchorX, int anchorY,
                                       int[] forward) {
        int leftX = -forward[1];
        int leftY = forward[0];
        return switch (role) {
            case COHORT_ESCORT -> new int[]{anchorX, anchorY};
            case LEAD_SCREEN -> new int[]{
                    anchorX - forward[0] * roleOrdinal
                            * ECHELON_DEPTH_SPACING,
                    anchorY - forward[1] * roleOrdinal
                            * ECHELON_DEPTH_SPACING};
            case LEFT_SCREEN -> new int[]{
                    anchorX + leftX * LATERAL_SLOT_SPACING
                            - forward[0] * (roleOrdinal + 1)
                            * ECHELON_DEPTH_SPACING,
                    anchorY + leftY * LATERAL_SLOT_SPACING
                            - forward[1] * (roleOrdinal + 1)
                            * ECHELON_DEPTH_SPACING};
            case RIGHT_SCREEN -> new int[]{
                    anchorX - leftX * LATERAL_SLOT_SPACING
                            - forward[0] * (roleOrdinal + 1)
                            * ECHELON_DEPTH_SPACING,
                    anchorY - leftY * LATERAL_SLOT_SPACING
                            - forward[1] * (roleOrdinal + 1)
                            * ECHELON_DEPTH_SPACING};
            case REAR_SCREEN -> new int[]{
                    anchorX - forward[0] * ECHELON_DEPTH_SPACING
                            * (roleOrdinal + 2),
                    anchorY - forward[1] * ECHELON_DEPTH_SPACING
                            * (roleOrdinal + 2)};
            default -> new int[]{anchorX, anchorY};
        };
    }

    private int roleOrdinal(CommandSquadState squad,
                            List<CommandSquadState> pool, Role role) {
        int ordinal = 0;
        for (CommandSquadState candidate : pool) {
            if (candidate.squadId() >= squad.squadId()) break;
            if (roles.get(candidate.squadId()) == role) ordinal++;
        }
        return ordinal;
    }

    private static int[] direction(int fromX, int fromY, int toX, int toY) {
        int dx = toX - fromX;
        int dy = toY - fromY;
        if (Math.abs(dx) >= Math.abs(dy) && dx != 0) {
            return new int[]{Integer.signum(dx), 0};
        }
        if (dy != 0) return new int[]{0, Integer.signum(dy)};
        return new int[]{1, 0};
    }

    private static int[] nearestReachable(
            CommandSquadState squad, int desiredX, int desiredY,
            int fallbackX, int fallbackY, CommandTopology topology,
            List<int[]> claimed) {
        int[] result = search(squad, desiredX, desiredY, topology, claimed);
        if (result != null) return result;
        result = search(squad, fallbackX, fallbackY, topology, claimed);
        if (result != null) return result;
        return search(squad, fallbackX, fallbackY, topology, List.of());
    }

    private static int[] search(CommandSquadState squad, int centerX,
                                int centerY, CommandTopology topology,
                                List<int[]> claimed) {
        for (int distance = 0; distance <= SLOT_SEARCH_RADIUS; distance++) {
            for (int y = centerY - distance; y <= centerY + distance; y++) {
                for (int x = centerX - distance; x <= centerX + distance; x++) {
                    if (Math.abs(x - centerX) + Math.abs(y - centerY)
                            != distance || !topology.isWalkable(x, y)
                            || !farEnough(x, y, claimed)) continue;
                    if (topology.reachable(squad.anchorCellX(),
                            squad.anchorCellY(), x, y)) return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static boolean farEnough(int x, int y, List<int[]> claimed) {
        int minimum = MIN_SLOT_SEPARATION * MIN_SLOT_SEPARATION;
        for (int[] cell : claimed) {
            int dx = x - cell[0];
            int dy = y - cell[1];
            if (dx * dx + dy * dy < minimum) return false;
        }
        return true;
    }

    private static SquadIntent externalIntent(CommandSquadState squad,
                                               AuthoredDuty duty) {
        Role role = duty == AuthoredDuty.SHELTER_GUARD
                ? Role.SHELTER_GUARD : duty == AuthoredDuty.PICKUP_GUARD
                ? Role.PICKUP_GUARD : Role.EXTERNAL;
        String reason = duty == AuthoredDuty.SHELTER_GUARD
                ? "AUTHORED_SHELTER_GUARD" : duty == AuthoredDuty.PICKUP_GUARD
                ? "AUTHORED_PICKUP_GUARD" : "EXTERNAL_OWNERSHIP_PRESERVED";
        ObjectiveAssignment assignment = squad.directive() != null
                ? squad.directive().assignment() : squad.assignment();
        return new SquadIntent(squad.squadId(), role, reason,
                assignment != null ? assignment.kind() : null,
                assignment != null ? assignment.targetCellX() : -1,
                assignment != null ? assignment.targetCellY() : -1,
                squad.localContact(), false);
    }

    private static String reason(Role role, boolean atShelter,
                                 boolean slowed) {
        String base = atShelter ? "SHELTER_RELIEF"
                : role == Role.COHORT_ESCORT ? "COHORT_ESCORT"
                : "MOVING_" + role.name();
        return slowed ? base + "_LOCAL_PRESSURE_HOLD" : base;
    }

    private static boolean terminal(ExtractionObjectiveFacts cohort) {
        return cohort.complete() || cohort.failed();
    }

    /** Radius searched for an open cell around a cohort centroid that is not one. */
    private static final int ANCHOR_SNAP_RADIUS = 6;

    /** The cell itself when it is open, else the nearest open one by ring, row then column; {@code null} for none. */
    private static int[] nearestWalkable(CommandTopology topology, int x, int y) {
        if (topology.inBounds(x, y) && topology.isWalkable(x, y)) return new int[]{x, y};
        for (int r = 1; r <= ANCHOR_SNAP_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx, cy = y + dy;
                    if (topology.inBounds(cx, cy) && topology.isWalkable(cx, cy)) return new int[]{cx, cy};
                }
            }
        }
        return null;
    }

    private static int validCell(int value, int fallback) {
        return value >= 0 ? value : fallback;
    }

    private static CommandProposal releaseOrRetain(CommandSquadState squad,
                                                    String reason) {
        return squad.directive() != null
                && "rescue-corridor".equals(squad.directive().issuer())
                ? CommandProposal.release(squad.squadId(),
                CommandAuthority.MISSION_COMMAND, reason,
                CommandStabilityBreak.OBJECTIVE_COMPLETED)
                : CommandProposal.retain(squad.squadId(),
                CommandAuthority.MISSION_COMMAND, reason);
    }

    private static CommandStabilityBreak stabilityBreak(
            CommandSquadState squad, ObjectiveAssignment assignment) {
        CommandDirective incumbent = squad.directive();
        return incumbent == null
                || Objects.equals(incumbent.assignment(), assignment)
                ? CommandStabilityBreak.NONE
                : CommandStabilityBreak.CONTEXT_INVALIDATED;
    }

    private static String summary(ExtractionObjectiveFacts cohort) {
        return cohort.payloadName() + "=" + cohort.phase().toLowerCase()
                + " active=" + cohort.activeElements()
                + " boarded=" + cohort.boardedElements()
                + " lost=" + cohort.lostElements();
    }
}
