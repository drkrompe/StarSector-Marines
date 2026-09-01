package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.AssaultDefenseSnapshot.AreaState;
import com.dillon.starsectormarines.battle.command.AssaultDefenseSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.AssaultDefenseSnapshot.Reason;
import com.dillon.starsectormarines.battle.command.AssaultDefenseSnapshot.ReportState;
import com.dillon.starsectormarines.battle.command.AssaultDefenseSnapshot.Role;
import com.dillon.starsectormarines.battle.command.AssaultDefenseSnapshot.SquadDirective;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceService;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.BeliefSource;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Defender strongpoint security with a belief-driven bounded mobile reserve. */
public final class AssaultDefenderCommand implements AutonomousMissionCommand<
        AssaultDefenderCommandFrame, AssaultDefenseSnapshot> {

    public static final int MAX_RESPONDERS_PER_AREA = 2;
    private static final int FRESH_DIRECT_TICKS =
            CommanderInfluenceService.UPDATE_INTERVAL_TICKS;
    private static final int BELIEF_LIFETIME_TICKS = Math.round(
            Squad.BELIEF_LIFETIME_SECONDS / BattleSimulation.TICK_DT);
    private static final int[][] RALLY_OFFSETS = {
            {0, -3}, {3, 0}, {0, 3}, {-3, 0},
            {2, -2}, {2, 2}, {-2, 2}, {-2, -2}, {0, 0}
    };

    private final Set<Integer> initialMobileSquads = new TreeSet<>();
    private final Set<Integer> heldReserveSquads = new TreeSet<>();
    private final Map<Integer, Integer> squadHomeArea = new HashMap<>();
    private final Map<Integer, Integer> squadRoutineArea = new HashMap<>();
    private final Map<Integer, Integer> squadResponseArea = new HashMap<>();
    private final Map<Long, ContactReport> contactReports = new HashMap<>();
    /**
     * Re-derived from the live candidate pool on every pulse rather than
     * latched from the first one. A squad may join the pool later — an
     * external owner releases it, or a handoff hands it to this strategy —
     * and a target frozen at the opening count would leave the commander
     * permanently unable to hold the reserve it now has the force for.
     */
    private int targetReserveCount;
    private int minimumRoutineCoverage;
    private volatile AssaultDefenseSnapshot defenseSnapshot =
            AssaultDefenseSnapshot.empty();

    private record Rally(int x, int y, int route) { }
    private record ContactReport(int area, int observedTick, int expiresTick,
                                 int activeUntilTick, float strength) { }

    public AssaultDefenderCommand(Set<Integer> mobileSquadIds) {
        if (mobileSquadIds != null) initialMobileSquads.addAll(mobileSquadIds);
    }

    @Override public Faction faction() { return Faction.DEFENDER; }
    @Override public String strategyId() { return "assault-defender"; }
    public AssaultDefenseSnapshot defenseSnapshot() { return defenseSnapshot; }

    @Override
    public CommandPlan<AssaultDefenseSnapshot> plan(
            AssaultDefenderCommandFrame frame) {
        AreaReports reports = reports(frame);
        Map<Integer, Integer> priorResponseAreas =
                new HashMap<>(squadResponseArea);
        Map<Integer, Integer> priorRoutineAreas =
                new HashMap<>(squadRoutineArea);
        Map<Integer, SquadDirective> directives = new LinkedHashMap<>();
        List<CommandSquadState> candidates = new ArrayList<>();

        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            int home = homeArea(squad, frame);
            CommandDirective incumbent = squad.directive();
            boolean handedToStrategy = incumbent != null
                    && incumbent.authority() == CommandAuthority.MISSION_COMMAND
                    && strategyId().equals(incumbent.issuer());
            if (!initialMobileSquads.contains(squad.squadId())
                    && !handedToStrategy) {
                Role role = squad.role() == UnitRole.GARRISON
                        ? Role.AUTHORED_POST : Role.EXTERNAL;
                Reason reason = role == Role.AUTHORED_POST
                        ? Reason.AUTHORED_POST_PRESERVED
                        : Reason.EXTERNAL_OWNERSHIP_PRESERVED;
                directives.put(squad.squadId(), new SquadDirective(
                        squad.squadId(), home, role, reason,
                        squad.assignment() != null ? squad.assignment().kind() : null,
                        -1, -1));
                continue;
            }
            if (incumbent != null && !strategyId().equals(incumbent.issuer())
                    && incumbent.authority().priority()
                    >= CommandAuthority.MISSION_COMMAND.priority()) {
                directives.put(squad.squadId(), new SquadDirective(
                        squad.squadId(), home, Role.EXTERNAL,
                        Reason.EXTERNAL_OWNERSHIP_PRESERVED,
                        incumbent.assignment() != null
                                ? incumbent.assignment().kind() : null,
                        incumbent.assignment() != null
                                ? incumbent.assignment().targetCellX() : -1,
                        incumbent.assignment() != null
                                ? incumbent.assignment().targetCellY() : -1));
                continue;
            }
            candidates.add(squad);
        }

        targetReserveCount = candidates.size() <= 1 ? 0
                : Math.min(2, Math.max(1, candidates.size() / 3));
        minimumRoutineCoverage = Math.min(frame.facts().areas().size(),
                Math.max(0, candidates.size() - targetReserveCount));
        refreshReserveMembership(candidates);

        List<CommandSquadState> routine = candidates.stream()
                .filter(squad -> !heldReserveSquads.contains(squad.squadId()))
                .toList();
        List<CommandSquadState> reserve = candidates.stream()
                .filter(squad -> heldReserveSquads.contains(squad.squadId()))
                .toList();

        int[] routineCoverage = new int[frame.facts().areas().size()];
        List<CommandSquadState> unallocatedRoutine = new ArrayList<>();
        for (CommandSquadState squad : routine) {
            Integer prior = squadRoutineArea.get(squad.squadId());
            if (prior == null || prior < 0 || prior >= routineCoverage.length
                    || routineCoverage[prior] > 0 || rallyFor(squad,
                    frame.facts().area(prior), 0, frame) == null) {
                unallocatedRoutine.add(squad);
                continue;
            }
            assignRoutine(squad, prior, routineCoverage, directives, frame);
        }
        for (CommandSquadState squad : unallocatedRoutine) {
            int areaIndex = coverageArea(squad, routineCoverage, frame);
            assignRoutine(squad, areaIndex, routineCoverage, directives, frame);
        }

        Set<Integer> selectedResponders = new HashSet<>();
        int[] responseCount = new int[frame.facts().areas().size()];
        List<Integer> threatened = reports.threatenedAreas();

        // Preserve a still-useful response before considering a new area.
        for (CommandSquadState squad : reserve) {
            Integer prior = priorResponseAreas.get(squad.squadId());
            if (prior == null || !threatened.contains(prior)
                    || responseCount[prior] >= MAX_RESPONDERS_PER_AREA) continue;
            if (assignResponder(squad, prior, responseCount[prior], reports,
                    directives, frame)) {
                selectedResponders.add(squad.squadId());
                responseCount[prior]++;
            }
        }
        // Each reported area receives one responder before any receives a second.
        for (int area : threatened) {
            if (responseCount[area] > 0) continue;
            CommandSquadState squad = chooseReserve(reserve, selectedResponders,
                    area, responseCount[area], frame);
            if (squad != null && assignResponder(squad, area,
                    responseCount[area], reports, directives, frame)) {
                selectedResponders.add(squad.squadId());
                responseCount[area]++;
            }
        }
        for (int area : threatened) {
            if (!reports.needsSecond(area)) continue;
            while (responseCount[area] < MAX_RESPONDERS_PER_AREA) {
                CommandSquadState squad = chooseReserve(reserve,
                        selectedResponders, area, responseCount[area], frame);
                if (squad == null || !assignResponder(squad, area,
                        responseCount[area], reports, directives, frame)) break;
                selectedResponders.add(squad.squadId());
                responseCount[area]++;
            }
        }

        for (CommandSquadState squad : reserve) {
            if (selectedResponders.contains(squad.squadId())) continue;
            int areaIndex = reachableArea(squad, homeArea(squad, frame), frame);
            Rally rally = areaIndex >= 0 ? rallyFor(squad,
                    frame.facts().area(areaIndex), 0, frame) : null;
            directives.put(squad.squadId(), new SquadDirective(
                    squad.squadId(), areaIndex, Role.RESERVE,
                    rally != null ? Reason.MOBILE_RESERVE_HELD
                            : Reason.NO_REACHABLE_AREA,
                    rally != null ? AssignmentKind.DEFEND_AREA : null,
                    rally != null ? rally.x() : -1,
                    rally != null ? rally.y() : -1));
        }

        List<CommandProposal> proposals = new ArrayList<>();
        for (CommandSquadState squad : candidates) {
            SquadDirective directive = directives.get(squad.squadId());
            if (directive != null && directive.assignmentKind()
                    == AssignmentKind.DEFEND_AREA) {
                ObjectiveAssignment assignment = ObjectiveAssignment.defendArea(
                        squad.squadId(), directive.markerCellX(),
                        directive.markerCellY());
                proposals.add(CommandProposal.assign(assignment,
                        CommandAuthority.MISSION_COMMAND,
                        directive.reason().name(), stabilityBreak(squad,
                                assignment, directive.areaIndex(),
                                directive.role(), priorRoutineAreas,
                                priorResponseAreas, reports, frame)));
            } else {
                Reason reason = directive != null ? directive.reason()
                        : Reason.NO_REACHABLE_AREA;
                CommandStabilityBreak stabilityBreak = squad.directive() != null
                        && strategyId().equals(squad.directive().issuer())
                        && squad.directive().assignment() != null
                        ? CommandStabilityBreak.CONTEXT_INVALIDATED
                        : CommandStabilityBreak.NONE;
                proposals.add(CommandProposal.claim(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND, reason.name(),
                        stabilityBreak));
            }
        }

        int reserveCount = (int) reserve.stream()
                .filter(squad -> !selectedResponders.contains(squad.squadId()))
                .filter(squad -> !squad.localContact()).count();
        Phase phase = threatened.isEmpty() ? Phase.AREA_SECURITY
                : Phase.REPORTED_CONTACT_RESPONSE;
        AssaultDefenseSnapshot detail = buildSnapshot(frame, phase,
                candidates.size(), reserveCount, directives, reports);
        List<String> objectives = detail.areas().stream()
                .map(area -> "area-" + area.index() + "=" + area.reportState()
                        + ":posts=" + area.strongpoints()
                        + ":response=" + area.respondingSquads())
                .toList();
        return new CommandPlan<>(faction(), strategyId(), phase.name(),
                frame.tick(), frame.influence() != null
                ? frame.influence().updatedTick() : -1,
                candidates.size(), reserveCount, objectives, proposals, detail);
    }

    @Override
    public CommanderSnapshot<AssaultDefenseSnapshot> reconcile(
            CommanderSnapshot<AssaultDefenseSnapshot> snapshot) {
        return snapshot.withDetail(snapshot.detail().reconcileStableDirectives(
                snapshot, defenseSnapshot, strategyId()));
    }

    @Override
    public void publish(CommanderSnapshot<AssaultDefenseSnapshot> snapshot) {
        defenseSnapshot = snapshot.detail();
        squadRoutineArea.clear();
        squadResponseArea.clear();
        for (SquadDirective directive : defenseSnapshot.directives()) {
            if (directive.role() == Role.ROUTINE_SECURITY
                    && directive.areaIndex() >= 0) {
                squadRoutineArea.put(directive.squadId(), directive.areaIndex());
            } else if (directive.role() == Role.RESPONDER
                    && directive.areaIndex() >= 0) {
                squadResponseArea.put(directive.squadId(), directive.areaIndex());
            }
        }
    }

    private void refreshReserveMembership(List<CommandSquadState> candidates) {
        Map<Integer, CommandSquadState> byId = new HashMap<>();
        for (CommandSquadState squad : candidates) byId.put(squad.squadId(), squad);
        heldReserveSquads.retainAll(byId.keySet());
        int desired = Math.min(targetReserveCount,
                Math.max(0, candidates.size() - minimumRoutineCoverage));
        List<CommandSquadState> ranked = candidates.stream()
                .sorted(Comparator.comparing(CommandSquadState::localContact)
                        .thenComparing(Comparator.comparingInt(
                                CommandSquadState::aliveMembers)
                        .reversed().thenComparing(Comparator.comparingInt(
                                CommandSquadState::squadId).reversed())))
                .toList();
        Set<Integer> selected = new HashSet<>();
        for (CommandSquadState squad : ranked) {
            if (selected.size() >= desired) break;
            if (squadResponseArea.containsKey(squad.squadId())) {
                selected.add(squad.squadId());
            }
        }
        for (CommandSquadState squad : ranked) {
            if (selected.size() >= desired) break;
            selected.add(squad.squadId());
        }
        heldReserveSquads.clear();
        heldReserveSquads.addAll(selected);
    }

    private static void assignRoutine(CommandSquadState squad, int areaIndex,
                                      int[] coverage,
                                      Map<Integer, SquadDirective> directives,
                                      AssaultDefenderCommandFrame frame) {
        Rally rally = areaIndex >= 0 ? rallyFor(squad,
                frame.facts().area(areaIndex), 0, frame) : null;
        if (rally == null) {
            directives.put(squad.squadId(), new SquadDirective(
                    squad.squadId(), -1, Role.ROUTINE_SECURITY,
                    Reason.NO_REACHABLE_AREA, null, -1, -1));
            return;
        }
        coverage[areaIndex]++;
        directives.put(squad.squadId(), new SquadDirective(
                squad.squadId(), areaIndex, Role.ROUTINE_SECURITY,
                Reason.ROUTINE_AREA_COVERAGE, AssignmentKind.DEFEND_AREA,
                rally.x(), rally.y()));
    }

    private boolean assignResponder(CommandSquadState squad, int areaIndex,
                                    int slot, AreaReports reports,
                                    Map<Integer, SquadDirective> directives,
                                    AssaultDefenderCommandFrame frame) {
        AssaultDefenderCommandFacts.Area area = frame.facts().area(areaIndex);
        Rally rally = rallyFor(squad, area, slot + 1, frame);
        if (rally == null) return false;
        Reason reason = reports.state(areaIndex) == ReportState.ACTIVE
                ? Reason.ACTIVE_CONTACT_RESPONSE
                : Reason.SUSPECTED_CONTACT_RESPONSE;
        directives.put(squad.squadId(), new SquadDirective(
                squad.squadId(), areaIndex, Role.RESPONDER, reason,
                AssignmentKind.DEFEND_AREA, rally.x(), rally.y()));
        return true;
    }

    private static CommandSquadState chooseReserve(
            List<CommandSquadState> reserve, Set<Integer> selected,
            int areaIndex, int slot, AssaultDefenderCommandFrame frame) {
        CommandSquadState best = null;
        int bestRoute = Integer.MAX_VALUE;
        for (CommandSquadState squad : reserve) {
            if (selected.contains(squad.squadId()) || squad.localContact()) {
                continue;
            }
            Rally rally = rallyFor(squad, frame.facts().area(areaIndex),
                    slot + 1, frame);
            if (rally == null) continue;
            if (rally.route() < bestRoute || rally.route() == bestRoute
                    && (best == null || squad.squadId() < best.squadId())) {
                best = squad;
                bestRoute = rally.route();
            }
        }
        return best;
    }

    private int homeArea(CommandSquadState squad,
                         AssaultDefenderCommandFrame frame) {
        return squadHomeArea.computeIfAbsent(squad.squadId(), ignored -> {
            int area = frame.facts().layout().sectorForCell(
                    squad.anchorCellX(), squad.anchorCellY());
            return area >= 0 ? area : 0;
        });
    }

    private static int reachableArea(CommandSquadState squad, int preferred,
                                     AssaultDefenderCommandFrame frame) {
        if (preferred >= 0 && rallyFor(squad, frame.facts().area(preferred),
                0, frame) != null) return preferred;
        int best = -1;
        int bestRoute = Integer.MAX_VALUE;
        for (AssaultDefenderCommandFacts.Area area : frame.facts().areas()) {
            Rally rally = rallyFor(squad, area, 0, frame);
            if (rally == null) continue;
            if (rally.route() < bestRoute || rally.route() == bestRoute
                    && area.index() < best) {
                best = area.index();
                bestRoute = rally.route();
            }
        }
        return best;
    }

    private static int coverageArea(CommandSquadState squad, int[] coverage,
                                    AssaultDefenderCommandFrame frame) {
        int best = -1;
        int bestCoverage = Integer.MAX_VALUE;
        int bestRoute = Integer.MAX_VALUE;
        int bestPriority = Integer.MIN_VALUE;
        for (AssaultDefenderCommandFacts.Area area : frame.facts().areas()) {
            Rally rally = rallyFor(squad, area, 0, frame);
            if (rally == null) continue;
            int assigned = coverage[area.index()];
            if (assigned < bestCoverage
                    || assigned == bestCoverage && rally.route() < bestRoute
                    || assigned == bestCoverage && rally.route() == bestRoute
                    && area.priority() > bestPriority
                    || assigned == bestCoverage && rally.route() == bestRoute
                    && area.priority() == bestPriority && area.index() < best) {
                best = area.index();
                bestCoverage = assigned;
                bestRoute = rally.route();
                bestPriority = area.priority();
            }
        }
        return best;
    }

    private static Rally rallyFor(CommandSquadState squad,
                                  AssaultDefenderCommandFacts.Area area,
                                  int slot,
                                  AssaultDefenderCommandFrame frame) {
        if (area == null) return null;
        Rally best = null;
        for (int i = 0; i < RALLY_OFFSETS.length; i++) {
            int[] offset = RALLY_OFFSETS[(slot + i) % RALLY_OFFSETS.length];
            int x = area.rallyX() + offset[0];
            int y = area.rallyY() + offset[1];
            if (x < area.minX() || x > area.maxX()
                    || y < area.minY() || y > area.maxY()
                    || !frame.topology().inBounds(x, y)
                    || !frame.topology().isWalkable(x, y)) continue;
            int route = frame.topology().routeLength(squad.anchorCellX(),
                    squad.anchorCellY(), x, y);
            if (route == Integer.MAX_VALUE) continue;
            if (best == null || route < best.route()) best = new Rally(x, y, route);
        }
        if (best != null) return best;
        for (int y = area.minY(); y <= area.maxY(); y++) {
            for (int x = area.minX(); x <= area.maxX(); x++) {
                if (!frame.topology().isWalkable(x, y)) continue;
                int route = frame.topology().routeLength(squad.anchorCellX(),
                        squad.anchorCellY(), x, y);
                if (route == Integer.MAX_VALUE) continue;
                int distance = Math.abs(x - area.rallyX())
                        + Math.abs(y - area.rallyY());
                if (best == null || distance < Math.abs(best.x() - area.rallyX())
                        + Math.abs(best.y() - area.rallyY())
                        || distance == Math.abs(best.x() - area.rallyX())
                        + Math.abs(best.y() - area.rallyY())
                        && route < best.route()) {
                    best = new Rally(x, y, route);
                }
            }
        }
        return best;
    }

    private CommandStabilityBreak stabilityBreak(
            CommandSquadState squad, ObjectiveAssignment assignment,
            int areaIndex, Role newRole,
            Map<Integer, Integer> priorRoutineAreas,
            Map<Integer, Integer> priorResponseAreas, AreaReports reports,
            AssaultDefenderCommandFrame frame) {
        CommandDirective incumbent = squad.directive();
        if (incumbent == null || incumbent.assignment() == null
                || !strategyId().equals(incumbent.issuer())
                || Objects.equals(incumbent.assignment(), assignment)) {
            return CommandStabilityBreak.NONE;
        }
        Integer oldArea = priorResponseAreas.get(squad.squadId());
        Integer oldRoutineArea = priorRoutineAreas.get(squad.squadId());
        if (newRole == Role.ROUTINE_SECURITY
                && !Objects.equals(oldRoutineArea, areaIndex)) {
            return CommandStabilityBreak.CONTEXT_INVALIDATED;
        }
        if (heldReserveSquads.contains(squad.squadId())
                && oldArea == null && newRole == Role.RESPONDER) {
            return CommandStabilityBreak.CONTEXT_INVALIDATED;
        }
        if (heldReserveSquads.contains(squad.squadId())
                && oldArea != null
                && (newRole != Role.RESPONDER || oldArea != areaIndex)
                && reports.state(oldArea) == ReportState.QUIET) {
            return CommandStabilityBreak.CONTEXT_INVALIDATED;
        }
        ObjectiveAssignment old = incumbent.assignment();
        if (!frame.topology().inBounds(old.targetCellX(), old.targetCellY())
                || !frame.topology().isWalkable(
                old.targetCellX(), old.targetCellY())
                || !frame.topology().reachable(
                squad.anchorCellX(), squad.anchorCellY(),
                old.targetCellX(), old.targetCellY())) {
            return CommandStabilityBreak.TARGET_UNREACHABLE;
        }
        return CommandStabilityBreak.NONE;
    }

    private static AssaultDefenseSnapshot buildSnapshot(
            AssaultDefenderCommandFrame frame, Phase phase, int mobilePool,
            int reserveCount, Map<Integer, SquadDirective> directives,
            AreaReports reports) {
        List<SquadDirective> directiveRows = new ArrayList<>(directives.values());
        directiveRows.sort(Comparator.comparingInt(SquadDirective::squadId));
        int areaCount = frame.facts().areas().size();
        int[] garrisons = new int[areaCount];
        int[] routine = new int[areaCount];
        int[] responders = new int[areaCount];
        int[] leadX = new int[areaCount];
        int[] leadY = new int[areaCount];
        java.util.Arrays.fill(leadX, -1);
        java.util.Arrays.fill(leadY, -1);
        for (SquadDirective directive : directiveRows) {
            int area = directive.areaIndex();
            if (area < 0 || area >= areaCount) continue;
            if (directive.role() == Role.AUTHORED_POST) garrisons[area]++;
            if (directive.role() == Role.ROUTINE_SECURITY) routine[area]++;
            if (directive.role() == Role.RESPONDER) responders[area]++;
            if (leadX[area] < 0 && directive.markerCellX() >= 0) {
                leadX[area] = directive.markerCellX();
                leadY[area] = directive.markerCellY();
            }
        }
        CommanderInfluenceSnapshot influence = frame.influence();
        List<AreaState> areaRows = new ArrayList<>(areaCount);
        for (AssaultDefenderCommandFacts.Area area : frame.facts().areas()) {
            areaRows.add(new AreaState(area.index(), area.minX(), area.minY(),
                    area.width(), area.height(), area.priority(),
                    area.strongpointIndexes().size(), garrisons[area.index()],
                    routine[area.index()], responders[area.index()],
                    reports.state(area.index()), reports.contacts[area.index()],
                    reports.freshest[area.index()], reports.expires[area.index()],
                    influence != null
                    ? influence.friendlyAtWorld(area.rallyX(), area.rallyY()) : 0f,
                    influence != null
                    ? influence.hostileAtWorld(area.rallyX(), area.rallyY()) : 0f,
                    leadX[area.index()], leadY[area.index()]));
        }
        List<AssaultDefenseSnapshot.StrongpointState> strongpoints =
                frame.facts().strongpoints().stream()
                        .map(point -> new AssaultDefenseSnapshot.StrongpointState(
                                point.index(), point.kind().name(),
                                point.areaIndex(), point.anchorX(), point.anchorY(),
                                point.rallyX(), point.rallyY(),
                                point.zoneId(), point.priority()))
                        .toList();
        List<AssaultDefenseSnapshot.SquadState> squads = frame.squads().stream()
                .map(squad -> new AssaultDefenseSnapshot.SquadState(
                        squad.squadId(), squad.aliveMembers(), squad.centroidX(),
                        squad.centroidY(), squad.currentZoneId(),
                        squad.executionSuspension(), squad.localContact()))
                .toList();
        return new AssaultDefenseSnapshot(frame.tick(), influence != null
                ? influence.updatedTick() : -1, frame.perspective(), phase,
                mobilePool, reserveCount, areaRows, strongpoints, squads,
                directiveRows);
    }

    private AreaReports reports(AssaultDefenderCommandFrame frame) {
        int count = frame.facts().areas().size();
        int[] contacts = new int[count];
        int[] freshest = new int[count];
        int[] expires = new int[count];
        float[] hostileStrength = new float[count];
        float[] friendlyStrength = new float[count];
        boolean[] active = new boolean[count];
        java.util.Arrays.fill(freshest, -1);
        java.util.Arrays.fill(expires, -1);
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence != null) {
            for (CommanderContact contact : influence.contacts()) {
                int area = frame.facts().layout().sectorForCell(
                        contact.cellX(), contact.cellY());
                if (area < 0) continue;
                ContactReport prior = contactReports.get(contact.unitId());
                if (prior != null && prior.area() != area) {
                    if (contact.source() != BeliefSource.DIRECT
                            && prior.activeUntilTick() >= frame.tick()) {
                        continue;
                    }
                    prior = null;
                }
                int candidateExpiry = frame.tick() + Math.max(1,
                        (int) Math.ceil(contact.confidence()
                                * BELIEF_LIFETIME_TICKS));
                int activeUntil = contact.source() == BeliefSource.DIRECT
                        ? contact.observedTick() + FRESH_DIRECT_TICKS
                        : prior != null ? prior.activeUntilTick() : -1;
                contactReports.put(contact.unitId(), new ContactReport(area,
                        Math.max(contact.observedTick(), prior != null
                                ? prior.observedTick() : -1),
                        Math.max(candidateExpiry, prior != null
                                ? prior.expiresTick() : -1), activeUntil,
                        Math.max(contact.confidence() * contact.strength(),
                                prior != null ? prior.strength() : 0f)));
            }
        }
        contactReports.entrySet().removeIf(entry ->
                entry.getValue().expiresTick() < frame.tick());
        for (ContactReport report : contactReports.values()) {
            if (report.area() < 0 || report.area() >= count) continue;
            contacts[report.area()]++;
            freshest[report.area()] = Math.max(freshest[report.area()],
                    report.observedTick());
            expires[report.area()] = Math.max(expires[report.area()],
                    report.expiresTick());
            hostileStrength[report.area()] += report.strength();
            active[report.area()] |= report.activeUntilTick() >= frame.tick();
        }
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            int area = frame.facts().layout().sectorForCell(
                    squad.anchorCellX(), squad.anchorCellY());
            if (area >= 0) friendlyStrength[area] += squad.aliveMembers();
        }
        return new AreaReports(contacts, freshest, expires, active,
                hostileStrength, friendlyStrength);
    }

    private record AreaReports(int[] contacts, int[] freshest, int[] expires,
                               boolean[] active, float[] hostileStrength,
                               float[] friendlyStrength) {
        private ReportState state(int area) {
            if (active[area]) return ReportState.ACTIVE;
            return contacts[area] > 0 ? ReportState.SUSPECTED : ReportState.QUIET;
        }

        private List<Integer> threatenedAreas() {
            List<Integer> result = new ArrayList<>();
            for (int i = 0; i < contacts.length; i++) {
                if (contacts[i] > 0) result.add(i);
            }
            result.sort(Comparator
                    .comparing((Integer area) -> state(area) != ReportState.ACTIVE)
                    .thenComparing((Integer area) -> -contacts[area])
                    .thenComparing((Integer area) -> -freshest[area])
                    .thenComparingInt(Integer::intValue));
            return result;
        }

        private boolean needsSecond(int area) {
            return hostileStrength[area] > friendlyStrength[area];
        }
    }
}
