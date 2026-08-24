package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.SquadDirective;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.TrackState;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Defender-side Conquest command: faction-honest first contact mobilizes a bounded patrol reserve. */
public final class ConquestDefenderCommand implements ConquestFrontCommand {

    static final int MIN_MOBILE_RESERVE = 1;
    static final int MAX_RESPONDERS_PER_TRACK = 2;
    static final int COARSE_BAND_CELLS = 16;
    static final int RALLY_REAR_OFFSET_CELLS = 6;
    private static final int RALLY_SNAP_RADIUS = 12;

    private final ConquestTrackLayout trackLayout;
    private final Set<Integer> initialMobileSquads = new TreeSet<>();
    private final Set<Integer> commandOwnedSquads = new HashSet<>();
    private final Map<Integer, Integer> homeTracks = new HashMap<>();
    private boolean initialPoolCaptured;
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

    private record Rally(int x, int y) { }
    private record CandidateChoice(Squad squad, Rally rally) { }

    private static final int[][] RALLY_ALTERNATIVES = {
            {0, 0}, {-4, 0}, {4, 0}, {0, -4}, {0, 4},
            {-8, 0}, {8, 0}, {0, -8}, {0, 8}
    };

    public ConquestDefenderCommand(ConquestTrackLayout trackLayout) {
        this.trackLayout = trackLayout;
        this.frontSnapshot = ConquestFrontSnapshot.empty(
                Faction.DEFENDER, trackLayout.axis());
    }

    @Override public Faction faction() { return Faction.DEFENDER; }
    @Override public ConquestFrontSnapshot frontSnapshot() { return frontSnapshot; }

    /** Freezes the setup-time patrol pool before any reinforcement delivery can occur. */
    public void captureStartingForce(BattleView sim) {
        if (!initialPoolCaptured) captureInitialMobilePool(sim);
    }

    @Override
    public void tick(BattleView sim) {
        captureStartingForce(sim);

        CommanderInfluenceSnapshot influence = sim.getCommanderInfluence(Faction.DEFENDER);
        Threat[] threats = buildThreats(influence);
        Map<Integer, Rally> rallies = new HashMap<>();
        for (Threat threat : threats) {
            if (threat.active()) rallies.put(threat.track, rallyFor(threat, sim));
        }

        List<Squad> candidates = new ArrayList<>();
        Map<Integer, SquadDirective> directives = new TreeMap<>();
        for (Squad squad : sortedDefenderSquads(sim)) {
            int home = homeTrack(squad);
            if (!initialMobileSquads.contains(squad.id)) {
                if (isGarrisonSquad(squad, sim)) {
                    directives.put(squad.id, directive(squad, home, home,
                            AssignmentReason.DEFENDER_GARRISON_HOLD));
                }
                continue;
            }
            if (squad.aliveMembers <= 0) continue;
            if (squad.hasBelievedContacts()) {
                clearCommandAssignmentIfOwned(squad);
                directives.put(squad.id, directive(squad, home, home,
                        AssignmentReason.DEFENDER_LOCAL_CONTACT));
                continue;
            }
            ObjectiveAssignment assignment = squad.assignedObjective;
            if (assignment != null && assignment.kind() != AssignmentKind.DEFEND_TRACK) {
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
                    threat.track, rallies.get(threat.track), sim);
            if (choice != null) assignResponse(choice, threat.track,
                    directives, selected);
        }
        // A high-pressure track may receive one additional squad, but never the whole reserve.
        for (Threat threat : activeThreats) {
            while (selected.size() < responseBudget
                    && respondersFor(threat.track, directives) < MAX_RESPONDERS_PER_TRACK) {
                CandidateChoice choice = chooseCandidate(candidates, selected,
                        threat.track, rallies.get(threat.track), sim);
                if (choice == null) break;
                assignResponse(choice, threat.track, directives, selected);
            }
        }

        for (Squad squad : candidates) {
            if (selected.contains(squad.id)) continue;
            clearCommandAssignmentIfOwned(squad);
            int home = homeTrack(squad);
            directives.put(squad.id, directive(squad, home, home,
                    AssignmentReason.DEFENDER_RESERVE_HOLD));
        }
        releaseOldCommandAssignments(sim, selected);
        commandOwnedSquads.clear();
        commandOwnedSquads.addAll(selected);

        publishSnapshot(sim, influence, threats, directives,
                activeThreats.isEmpty() ? Phase.LANE_ADVANCE : Phase.FRONT_ADJUST);
    }

    private void captureInitialMobilePool(BattleView sim) {
        for (Squad squad : sortedDefenderSquads(sim)) {
            if (squad.aliveMembers <= 0 || !isPatrolSquad(squad, sim)) continue;
            initialMobileSquads.add(squad.id);
            homeTracks.put(squad.id, trackFor(squad));
        }
        initialPoolCaptured = true;
    }

    private List<Squad> sortedDefenderSquads(BattleView sim) {
        List<Squad> result = new ArrayList<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction == Faction.DEFENDER) result.add(squad);
        }
        result.sort(Comparator.comparingInt(s -> s.id));
        return result;
    }

    private boolean isPatrolSquad(Squad squad, BattleView sim) {
        int count = sim.squadMemberCount(squad.id);
        return count > 0 && sim.role().role(sim.squadMemberAt(squad.id, 0)) == UnitRole.PATROL;
    }

    private boolean isGarrisonSquad(Squad squad, BattleView sim) {
        int count = sim.squadMemberCount(squad.id);
        return count > 0 && sim.role().role(sim.squadMemberAt(squad.id, 0)) == UnitRole.GARRISON;
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

    private Rally rallyFor(Threat threat, BattleView sim) {
        int bandStart = Math.max(0, threat.deepestForward / COARSE_BAND_CELLS
                * COARSE_BAND_CELLS);
        int forward = Math.min(trackLayout.forwardExtent() - 1,
                bandStart + COARSE_BAND_CELLS + RALLY_REAR_OFFSET_CELLS);
        int lateral = trackLayout.lateralCenterCell(threat.track);
        int x = trackLayout.cellX(lateral, forward);
        int y = trackLayout.cellY(lateral, forward);
        return snapToTrackWalkable(x, y, threat.track, sim.getGrid());
    }

    private Rally snapToTrackWalkable(int desiredX, int desiredY, int track,
                                      NavigationGrid grid) {
        for (int radius = 0; radius <= RALLY_SNAP_RADIUS; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int dx = radius - Math.abs(dy);
                Rally left = validRally(desiredX - dx, desiredY + dy, track, grid);
                if (left != null) return left;
                if (dx != 0) {
                    Rally right = validRally(desiredX + dx, desiredY + dy, track, grid);
                    if (right != null) return right;
                }
            }
        }
        return new Rally(desiredX, desiredY);
    }

    private Rally validRally(int x, int y, int track, NavigationGrid grid) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y)
                && trackLayout.trackForCell(x, y) == track ? new Rally(x, y) : null;
    }

    private CandidateChoice chooseCandidate(List<Squad> candidates,
                                            Set<Integer> selected,
                                            int effectiveTrack, Rally coarseRally,
                                            BattleView sim) {
        CandidateChoice best = null;
        long bestScore = Long.MAX_VALUE;
        for (Squad squad : candidates) {
            if (selected.contains(squad.id)) continue;
            int home = homeTrack(squad);
            int trackDistance = Math.abs(home - effectiveTrack);
            if (trackDistance > 1) continue;
            Rally rally = reachableRally(squad, coarseRally, effectiveTrack, sim);
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
        Squad squad = choice.squad();
        Rally rally = choice.rally();
        ObjectiveAssignment next = ObjectiveAssignment.defendTrack(
                squad.id, rally.x, rally.y);
        if (!next.equals(squad.assignedObjective)) squad.assignedObjective = next;
        int home = homeTrack(squad);
        AssignmentReason reason = home == effectiveTrack
                ? AssignmentReason.DEFENDER_TRACK_RESPONSE
                : AssignmentReason.DEFENDER_ADJACENT_TRACK_RESPONSE;
        directives.put(squad.id, new SquadDirective(squad.id, home,
                effectiveTrack, reason, AssignmentKind.DEFEND_TRACK, -1,
                rally.x, rally.y));
        selected.add(squad.id);
    }

    /**
     * Resolves the coarse command point to a cell this particular squad can
     * actually reach. Door-connected compounds work normally; a sealed room
     * rejects the candidate so it cannot consume a response slot while a
     * reachable reserve remains available.
     */
    private Rally reachableRally(Squad squad, Rally desired, int track,
                                 BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        long anchor = sim.resolveUnit(squad.leaderId);
        if (anchor == 0L && sim.squadMemberCount(squad.id) > 0) {
            anchor = sim.squadMemberAt(squad.id, 0);
        }
        if (anchor == 0L) return null;
        int startX = sim.world().cellX(anchor);
        int startY = sim.world().cellY(anchor);
        if (!grid.inBounds(startX, startY) || !grid.isWalkable(startX, startY)) return null;
        for (int[] offset : RALLY_ALTERNATIVES) {
            Rally candidate = validRally(desired.x + offset[0],
                    desired.y + offset[1], track, grid);
            if (candidate != null && reachable(startX, startY, candidate, grid)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean reachable(int startX, int startY, Rally rally,
                              NavigationGrid grid) {
        return !Paths.isEmpty(GridPathfinder.findPath(grid, startX, startY,
                rally.x, rally.y));
    }

    private int respondersFor(int track, Map<Integer, SquadDirective> directives) {
        int count = 0;
        for (SquadDirective directive : directives.values()) {
            if (directive.effectiveTrack() == track
                    && (directive.reason() == AssignmentReason.DEFENDER_TRACK_RESPONSE
                    || directive.reason() == AssignmentReason.DEFENDER_ADJACENT_TRACK_RESPONSE)) count++;
        }
        return count;
    }

    private void releaseOldCommandAssignments(BattleView sim, Set<Integer> retained) {
        for (int squadId : commandOwnedSquads) {
            if (retained.contains(squadId)) continue;
            Squad squad = sim.getSquad(squadId);
            if (squad != null && squad.assignedObjective != null
                    && squad.assignedObjective.kind() == AssignmentKind.DEFEND_TRACK) {
                squad.assignedObjective = null;
            }
        }
    }

    private void clearCommandAssignmentIfOwned(Squad squad) {
        if (commandOwnedSquads.contains(squad.id)
                && squad.assignedObjective != null
                && squad.assignedObjective.kind() == AssignmentKind.DEFEND_TRACK) {
            squad.assignedObjective = null;
        }
    }

    private SquadDirective directive(Squad squad, int preferred, int effective,
                                     AssignmentReason reason) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        return new SquadDirective(squad.id, preferred, effective, reason,
                assignment != null ? assignment.kind() : null,
                assignment != null ? assignment.targetZoneId() : -1,
                assignment != null ? assignment.targetCellX() : -1,
                assignment != null ? assignment.targetCellY() : -1);
    }

    private int homeTrack(Squad squad) {
        return homeTracks.computeIfAbsent(squad.id, ignored -> trackFor(squad));
    }

    private int trackFor(Squad squad) {
        int track = trackLayout.trackForLateral(trackLayout.lateralCoordinate(
                squad.centroidX, squad.centroidY));
        return Math.max(0, Math.min(trackLayout.trackCount() - 1, track));
    }

    private void publishSnapshot(BattleView sim, CommanderInfluenceSnapshot influence,
                                 Threat[] threats,
                                 Map<Integer, SquadDirective> directives,
                                 Phase phase) {
        int tracks = trackLayout.trackCount();
        int[] preferredSquads = new int[tracks];
        int[] effectiveSquads = new int[tracks];
        int[] effectiveMembers = new int[tracks];
        int[] preferredMembers = new int[tracks];
        float[] bodyProgress = new float[tracks];
        float[] leadProgress = new float[tracks];
        Arrays.fill(leadProgress, -1f);

        for (Squad squad : sortedDefenderSquads(sim)) {
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
        for (CompoundService.Record record : sim.getCompoundService().getRecords()) {
            if (record.state != CompoundService.CompoundState.MARINE_HELD) remainingCompounds++;
            if (record.node.kind == TacticalNode.Kind.COMMAND_POST) {
                keepZone = sim.getZoneGraph().zoneIdAt(record.node.anchorX, record.node.anchorY);
                keepState = record.state;
            }
        }
        frontSnapshot = new ConquestFrontSnapshot(sim.getSimTickIndex(),
                influence != null ? influence.updatedTick() : -1,
                Faction.DEFENDER, trackLayout.axis(), phase,
                remainingCompounds, keepZone, keepState,
                states, new ArrayList<>(directives.values()));
    }
}
