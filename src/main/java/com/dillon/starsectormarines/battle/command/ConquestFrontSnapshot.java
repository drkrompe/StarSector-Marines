package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

import java.util.ArrayList;
import java.util.List;

/** Immutable command-authored explanation of the current Conquest front. */
public record ConquestFrontSnapshot(
        int tick,
        int influenceTick,
        Faction perspective,
        TraversalAxis axis,
        Phase phase,
        int remainingCompounds,
        int keepZoneId,
        CompoundState keepState,
        List<TrackState> tracks,
        List<SquadState> squads,
        List<SquadDirective> directives) {

    public enum Phase {
        LANE_ADVANCE,
        FRONT_ADJUST,
        KEEP_CONVERGENCE,
        FINAL_COMPOUND_CONVERGENCE
    }

    public enum AssignmentReason {
        GARRISON_HOLD,
        EXTERNAL_OWNERSHIP_PRESERVED,
        COMPOUND_CAPTURE_PRESERVED,
        COMPOUND_CAPTURE_UNCONTESTED,
        COMPOUND_ASSAULT_ADJACENT,
        TRACK_ADVANCE,
        TRACK_LINE_ADVANCE,
        ADJACENT_TRACK_SUPPORT,
        KEEP_APPROACH,
        FINAL_COMPOUND_SUPPORT,
        NO_REACHABLE_COMPOUND_TARGET,
        NO_ACTIONABLE_TRACK_TARGET,
        DEFENDER_GARRISON_HOLD,
        DEFENDER_LOCAL_CONTACT,
        DEFENDER_TRACK_RESPONSE,
        DEFENDER_ADJACENT_TRACK_RESPONSE,
        DEFENDER_RELIEF_OBJECTIVE,
        DEFENDER_RESERVE_HOLD,
        DEFENDER_EXTERNAL_ASSIGNMENT_PRESERVED
    }

    public record TrackState(
            int index,
            int lateralStart,
            int lateralEnd,
            int preferredSquads,
            int effectiveSquads,
            int effectiveLiveMembers,
            float friendlyBodyProgress,
            float friendlyLeadProgress,
            float knownHostileFrontProgress,
            int knownHostileContacts,
            float friendlyPressure,
            float knownHostilePressure,
            int targetZoneId) { }

    /** Frozen own-force physical facts published with this command pulse. */
    public record SquadState(
            int squadId,
            int aliveMembers,
            float centroidX,
            float centroidY,
            int currentZoneId,
            String executionSuspension,
            boolean localContact,
            int activePathMembers,
            int membersInTargetZone) {

        public SquadState(int squadId, int aliveMembers, float centroidX,
                          float centroidY, int currentZoneId,
                          String executionSuspension, boolean localContact,
                          int activePathMembers) {
            this(squadId, aliveMembers, centroidX, centroidY, currentZoneId,
                    executionSuspension, localContact, activePathMembers, 0);
        }

        public SquadState(int squadId, int aliveMembers, float centroidX,
                          float centroidY, int currentZoneId,
                          String executionSuspension, boolean localContact) {
            this(squadId, aliveMembers, centroidX, centroidY, currentZoneId,
                    executionSuspension, localContact, 0, 0);
        }
    }

    public record SquadDirective(
            int squadId,
            int preferredTrack,
            int effectiveTrack,
            AssignmentReason reason,
            AssignmentKind assignmentKind,
            int targetZoneId,
            int targetCellX,
            int targetCellY,
            int markerCellX,
            int markerCellY,
            boolean distantCaptureDeferred) {

        public SquadDirective(int squadId, int preferredTrack,
                              int effectiveTrack, AssignmentReason reason,
                              AssignmentKind assignmentKind, int targetZoneId,
                              int targetCellX, int targetCellY,
                              int markerCellX, int markerCellY) {
            this(squadId, preferredTrack, effectiveTrack, reason,
                    assignmentKind, targetZoneId, targetCellX, targetCellY,
                    markerCellX, markerCellY, false);
        }

        public SquadDirective(int squadId, int preferredTrack,
                              int effectiveTrack, AssignmentReason reason,
                              AssignmentKind assignmentKind, int targetZoneId) {
            this(squadId, preferredTrack, effectiveTrack, reason,
                    assignmentKind, targetZoneId, -1, -1, -1, -1);
        }

        public SquadDirective(int squadId, int preferredTrack,
                              int effectiveTrack, AssignmentReason reason,
                              AssignmentKind assignmentKind, int targetZoneId,
                              int targetCellX, int targetCellY) {
            this(squadId, preferredTrack, effectiveTrack, reason,
                    assignmentKind, targetZoneId, targetCellX, targetCellY,
                    targetCellX, targetCellY);
        }

        public SquadDirective withDistantCaptureDeferred() {
            return new SquadDirective(squadId, preferredTrack, effectiveTrack,
                    reason, assignmentKind, targetZoneId, targetCellX,
                    targetCellY, markerCellX, markerCellY, true);
        }
    }

    public ConquestFrontSnapshot {
        tracks = List.copyOf(tracks);
        squads = List.copyOf(squads);
        directives = List.copyOf(directives);
    }

    public ConquestFrontSnapshot(int tick, int influenceTick,
                                 Faction perspective, TraversalAxis axis,
                                 Phase phase, int remainingCompounds,
                                 int keepZoneId, CompoundState keepState,
                                 List<TrackState> tracks,
                                 List<SquadDirective> directives) {
        this(tick, influenceTick, perspective, axis, phase,
                remainingCompounds, keepZoneId, keepState, tracks,
                List.of(), directives);
    }

    /** Back-compatible marine-perspective constructor for fixtures and older callers. */
    public ConquestFrontSnapshot(int tick, int influenceTick,
                                 TraversalAxis axis, Phase phase,
                                 int remainingCompounds, int keepZoneId,
                                 CompoundState keepState,
                                 List<TrackState> tracks,
                                 List<SquadState> squads,
                                 List<SquadDirective> directives) {
        this(tick, influenceTick, Faction.MARINE, axis, phase,
                remainingCompounds, keepZoneId, keepState, tracks,
                squads, directives);
    }

    /** Back-compatible marine-perspective constructor for fixtures and older callers. */
    public ConquestFrontSnapshot(int tick, int influenceTick,
                                 TraversalAxis axis, Phase phase,
                                 int remainingCompounds, int keepZoneId,
                                 CompoundState keepState,
                                 List<TrackState> tracks,
                                 List<SquadDirective> directives) {
        this(tick, influenceTick, Faction.MARINE, axis, phase,
                remainingCompounds, keepZoneId, keepState, tracks,
                List.of(), directives);
    }

    public SquadDirective directiveFor(int squadId) {
        for (SquadDirective directive : directives) {
            if (directive.squadId() == squadId) return directive;
        }
        return null;
    }

    public SquadState squadFor(int squadId) {
        for (SquadState squad : squads) {
            if (squad.squadId() == squadId) return squad;
        }
        return null;
    }

    public TrackState track(int index) {
        for (TrackState track : tracks) {
            if (track.index() == index) return track;
        }
        return null;
    }

    /** Replaces stability-held proposal rows with the arbiter's effective order. */
    public ConquestFrontSnapshot reconcileStableDirectives(
            CommanderSnapshot<?> committed, ConquestFrontSnapshot prior,
            String strategy) {
        List<SquadDirective> reconciled = new ArrayList<>(directives.size());
        for (SquadDirective planned : directives) {
            CommandDirective result = committed.directiveFor(planned.squadId());
            if (result == null || result.status() != CommandDirective.Status.RETAINED
                    || !strategy.equals(result.issuer())
                    || !result.dispositionReason().startsWith("stable through tick")) {
                reconciled.add(planned);
                continue;
            }
            SquadDirective previous = prior != null
                    ? prior.directiveFor(planned.squadId()) : null;
            int preferred = previous != null
                    ? previous.preferredTrack() : planned.preferredTrack();
            int effective = previous != null
                    ? previous.effectiveTrack() : planned.effectiveTrack();
            ObjectiveAssignment assignment = result.assignment();
            AssignmentReason effectiveReason = AssignmentReason.valueOf(
                    result.reason());
            int targetCellX = assignment != null
                    ? assignment.targetCellX() : -1;
            int targetCellY = assignment != null
                    ? assignment.targetCellY() : -1;
            int markerCellX = targetCellX;
            int markerCellY = targetCellY;
            if (markerCellX < 0 && previous != null) {
                markerCellX = previous.markerCellX();
                markerCellY = previous.markerCellY();
            }
            reconciled.add(new SquadDirective(planned.squadId(), preferred,
                    effective, effectiveReason,
                    assignment != null ? assignment.kind() : null,
                    assignment != null ? assignment.targetZoneId() : -1,
                    targetCellX, targetCellY, markerCellX, markerCellY,
                    planned.distantCaptureDeferred()));
        }
        return new ConquestFrontSnapshot(tick, influenceTick, perspective,
                axis, phase, remainingCompounds, keepZoneId, keepState,
                tracks, squads, reconciled);
    }

    public static ConquestFrontSnapshot empty(Faction perspective,
                                               TraversalAxis axis) {
        return new ConquestFrontSnapshot(-1, -1, perspective, axis, Phase.LANE_ADVANCE,
                -1, -1, null, List.of(), List.of(), List.of());
    }

    public static ConquestFrontSnapshot empty(TraversalAxis axis) {
        return empty(Faction.MARINE, axis);
    }
}
