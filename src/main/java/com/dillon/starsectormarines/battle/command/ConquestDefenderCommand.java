package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.SquadDirective;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.TrackState;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.command.reinforcement.ConvoyDeployment;
import com.dillon.starsectormarines.battle.command.reinforcement.ConvoyDeploymentPolicy;
import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementRequest;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

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

/** Defender-side Conquest command: faction-honest first contact mobilizes a bounded patrol reserve. */
public final class ConquestDefenderCommand implements ConquestFrontCommand,
        AutonomousMissionCommand<ConquestCommandFrame, ConquestFrontSnapshot>,
        ConvoyDeploymentPolicy {

    static final int MIN_MOBILE_RESERVE = 1;
    static final int MAX_RESPONDERS_PER_TRACK = 2;
    static final int COARSE_BAND_CELLS = 16;
    static final int RALLY_REAR_OFFSET_CELLS = 6;
    static final int CONVOY_REAR_STANDOFF_CELLS = 12;
    static final int CONVOY_DEPLOYMENT_BAND_CELLS = 4;
    private static final int RALLY_SNAP_RADIUS = 12;

    private final ConquestTrackLayout trackLayout;
    private final Set<Integer> initialMobileSquads = new TreeSet<>();
    private final Map<Integer, Integer> homeTracks = new HashMap<>();
    private volatile ConquestFrontSnapshot frontSnapshot;

    private static final class Threat {
        final int track;
        int contacts;
        int deepestForward = -1;
        float knownHostileFront = -1f;
        float friendlyPressure;
        float hostilePressure;

        Threat(int track) { this.track = track; }
        boolean active() { return contacts > 0; }
    }

    /** Mutable working copy; never exposes or mutates a live squad. */
    private static final class PlanningSquad {
        final int id;
        final int aliveMembers;
        final float centroidX;
        final float centroidY;
        final int anchorCellX;
        final int anchorCellY;
        final int currentZoneId;
        final UnitRole role;
        final boolean localContact;
        final String executionSuspension;
        final int activePathMembers;
        final CommandDirective originalDirective;
        ObjectiveAssignment assignedObjective;

        PlanningSquad(CommandSquadState state) {
            id = state.squadId();
            aliveMembers = state.aliveMembers();
            centroidX = state.centroidX();
            centroidY = state.centroidY();
            anchorCellX = state.anchorCellX();
            anchorCellY = state.anchorCellY();
            currentZoneId = state.currentZoneId();
            role = state.role();
            localContact = state.localContact();
            executionSuspension = state.executionSuspension();
            activePathMembers = state.activePathMembers();
            originalDirective = state.directive();
            assignedObjective = state.assignment();
        }
    }

    private record Rally(int x, int y) { }
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
        CommanderInfluenceSnapshot influence = frame.influence();
        Threat[] threats = buildThreats(influence);
        Map<Integer, Rally> rallies = new HashMap<>();
        for (Threat threat : threats) {
            if (threat.active()) rallies.put(threat.track,
                    rallyFor(threat, frame.topology()));
        }

        List<PlanningSquad> candidates = new ArrayList<>();
        Map<Integer, PlanningSquad> allSquads = new TreeMap<>();
        Map<Integer, SquadDirective> directives = new TreeMap<>();
        for (CommandSquadState state : frame.squads()) {
            PlanningSquad squad = new PlanningSquad(state);
            allSquads.put(squad.id, squad);
            if (squad.aliveMembers <= 0) continue;
            int home = homeTrack(squad);
            if (!isCommandPoolSquad(squad)) {
                AssignmentReason reason = squad.role == UnitRole.GARRISON
                        ? AssignmentReason.DEFENDER_GARRISON_HOLD
                        : AssignmentReason.DEFENDER_EXTERNAL_ASSIGNMENT_PRESERVED;
                directives.put(squad.id, directive(squad, home, home, reason));
                continue;
            }
            if (squad.localContact) {
                clearMissionRally(squad);
                directives.put(squad.id, directive(squad, home, home,
                        AssignmentReason.DEFENDER_LOCAL_CONTACT));
                continue;
            }
            if (squad.assignedObjective != null
                    && squad.assignedObjective.kind() != AssignmentKind.DEFEND_TRACK) {
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

        int responseBudget = candidates.size() >= 2
                ? candidates.size() - MIN_MOBILE_RESERVE : candidates.size();
        Set<Integer> selected = new HashSet<>();
        List<Threat> activeThreats = Arrays.stream(threats)
                .filter(Threat::active)
                .sorted(Comparator.comparingInt((Threat t) -> -t.contacts)
                        .thenComparingInt(t -> t.track))
                .toList();

        // First pass spreads the response across distinct threatened tracks.
        for (Threat threat : activeThreats) {
            if (selected.size() >= responseBudget) break;
            CandidateChoice choice = chooseCandidate(candidates, selected,
                    threat.track, rallies.get(threat.track), frame.topology());
            if (choice != null) assignResponse(choice, threat.track,
                    directives, selected);
        }
        // A high-pressure track may receive one additional squad, but never the whole reserve.
        for (Threat threat : activeThreats) {
            while (selected.size() < responseBudget
                    && respondersFor(threat.track, directives) < MAX_RESPONDERS_PER_TRACK) {
                CandidateChoice choice = chooseCandidate(candidates, selected,
                        threat.track, rallies.get(threat.track), frame.topology());
                if (choice == null) break;
                assignResponse(choice, threat.track, directives, selected);
            }
        }

        for (PlanningSquad squad : candidates) {
            if (selected.contains(squad.id)) continue;
            clearMissionRally(squad);
            int home = homeTrack(squad);
            directives.put(squad.id, directive(squad, home, home,
                    AssignmentReason.DEFENDER_RESERVE_HOLD));
        }

        Phase phase = activeThreats.isEmpty() ? Phase.LANE_ADVANCE : Phase.FRONT_ADJUST;
        ConquestFrontSnapshot detail = buildFrontSnapshot(frame, influence,
                threats, directives, allSquads, phase);
        List<CommandProposal> proposals = buildProposals(
                frame, allSquads, directives, threats);
        return new CommandPlan<>(faction(), strategyId(), phase.name(), frame.tick(),
                influence != null ? influence.updatedTick() : -1,
                candidates.size(), candidates.size() - selected.size(),
                List.of("active threat tracks=" + activeThreats.size()),
                proposals, detail);
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

    private Threat[] buildThreats(CommanderInfluenceSnapshot influence) {
        Threat[] threats = new Threat[trackLayout.trackCount()];
        for (int i = 0; i < threats.length; i++) threats[i] = new Threat(i);
        if (influence == null) return threats;

        for (CommanderContact contact : influence.contacts()) {
            int track = trackLayout.trackForCell(contact.cellX(), contact.cellY());
            if (track < 0 || track >= threats.length) continue;
            Threat threat = threats[track];
            threat.contacts++;
            int forward = Math.round(trackLayout.forwardCoordinate(
                    contact.cellX(), contact.cellY()));
            threat.deepestForward = Math.max(threat.deepestForward, forward);
            threat.knownHostileFront = Math.max(threat.knownHostileFront,
                    trackLayout.assaultProgress(contact.cellX(), contact.cellY()));
        }
        for (int by = 0; by < influence.height(); by++) {
            for (int bx = 0; bx < influence.width(); bx++) {
                int x = influence.blockWorldX(bx) + influence.blockWorldWidth(bx) / 2;
                int y = influence.blockWorldY(by) + influence.blockWorldHeight(by) / 2;
                int track = trackLayout.trackForCell(x, y);
                if (track < 0 || track >= threats.length) continue;
                threats[track].friendlyPressure += influence.friendlyAt(bx, by);
                threats[track].hostilePressure += influence.hostileAt(bx, by);
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

    private CandidateChoice chooseCandidate(List<PlanningSquad> candidates,
                                            Set<Integer> selected,
                                            int effectiveTrack, Rally coarseRally,
                                            CommandTopology topology) {
        CandidateChoice best = null;
        long bestScore = Long.MAX_VALUE;
        for (PlanningSquad squad : candidates) {
            if (selected.contains(squad.id)) continue;
            int home = homeTrack(squad);
            int trackDistance = Math.abs(home - effectiveTrack);
            if (trackDistance > 1) continue;
            Rally rally = reachableRally(squad, coarseRally, effectiveTrack, topology);
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
                return candidate;
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

    private boolean hasHigherAuthority(PlanningSquad squad) {
        return squad.originalDirective != null
                && squad.originalDirective.authority().priority()
                > CommandAuthority.MISSION_COMMAND.priority();
    }

    private boolean isCommandPoolSquad(PlanningSquad squad) {
        if (initialMobileSquads.contains(squad.id)) return true;
        return squad.originalDirective != null
                && squad.originalDirective.authority() == CommandAuthority.MISSION_COMMAND
                && strategyId().equals(squad.originalDirective.issuer());
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
    public ConvoyDeployment deploymentFor(ReinforcementRequest request) {
        int sourceX = request.hasObjective() ? request.objectiveX : request.rallyX;
        int sourceY = request.hasObjective() ? request.objectiveY : request.rallyY;
        int track = trackLayout.trackForCell(sourceX, sourceY);
        if (track < 0 || track >= trackLayout.trackCount()) {
            return ConvoyDeployment.legacy(request);
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
        return new ConvoyDeployment(hintX, hintY, minimumForward,
                true, request.hasObjective(),
                SquadCommandClaim.mission(strategyId(),
                        "convoy relief " + request.reason.name()));
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
                    threat.friendlyPressure, threat.hostilePressure, -1));
        }

        int remainingCompounds = 0;
        int keepZone = -1;
        CompoundService.CompoundState keepState = null;
        for (ConquestCommandFacts.Compound compound : frame.facts().compounds()) {
            if (compound.state() != CompoundService.CompoundState.MARINE_HELD) {
                remainingCompounds++;
            }
            if (compound.node().kind == TacticalNode.Kind.COMMAND_POST) {
                keepZone = compound.anchorZoneId();
                keepState = compound.state();
            }
        }
        return new ConquestFrontSnapshot(frame.tick(),
                influence != null ? influence.updatedTick() : -1,
                Faction.DEFENDER, trackLayout.axis(), phase,
                remainingCompounds, keepZone, keepState,
                states, squadStates(squads),
                new ArrayList<>(directives.values()));
    }

    private static List<ConquestFrontSnapshot.SquadState> squadStates(
            Map<Integer, PlanningSquad> squads) {
        List<ConquestFrontSnapshot.SquadState> states = new ArrayList<>(squads.size());
        for (PlanningSquad squad : squads.values()) {
            states.add(new ConquestFrontSnapshot.SquadState(
                    squad.id, squad.aliveMembers, squad.centroidX,
                    squad.centroidY, squad.currentZoneId,
                    squad.executionSuspension, squad.localContact,
                    squad.activePathMembers));
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
                    && planned.assignedObjective.kind() != AssignmentKind.DEFEND_TRACK) {
                proposals.add(CommandProposal.retain(squadId,
                        CommandAuthority.MISSION_COMMAND, reason));
            } else if (planned.assignedObjective != null) {
                proposals.add(CommandProposal.assign(planned.assignedObjective,
                        CommandAuthority.MISSION_COMMAND, reason, stabilityBreak));
            } else if (frozen.assignment() != null) {
                proposals.add(CommandProposal.release(squadId,
                        CommandAuthority.MISSION_COMMAND, reason, stabilityBreak));
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
