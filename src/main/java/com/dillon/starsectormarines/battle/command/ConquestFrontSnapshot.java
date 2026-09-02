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
        /**
         * Squad's own track holds no believed hostile at all: advance abreast
         * of the neighbouring tracks rather than stand still waiting for a
         * sighting only advancing can produce.
         */
        TRACK_LINE_SCOUT_ADVANCE,
        /** Squad is in contact on a believed lane front: clear forward rather than stand off. */
        TRACK_LINE_ATTACK,
        ADJACENT_TRACK_SUPPORT,
        KEEP_APPROACH,
        FINAL_COMPOUND_SUPPORT,
        NO_REACHABLE_COMPOUND_TARGET,
        NO_ACTIONABLE_TRACK_TARGET,
        /**
         * Squad has no track work of its own <em>and</em> every capture slot
         * still open sits more than one track from home, so the capture
         * allocation refused it. Without this the pulse would publish the
         * generic no-actionable-target reason, which is the one thing a dump
         * cannot tell apart from an empty map — and the refusal it is hiding
         * is exactly the one that used to walk a squad the width of the map.
         */
        CAPTURE_OUT_OF_TRACK_REACH,
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
            int targetZoneId,
            int responderCap,
            int chainLinks,
            int chainLinksHeld,
            int chainFrontLink) {

        /**
         * A picture from a command whose map laid no lanes, or one taken before
         * the chain was read: {@code -1} for both chain fields reads as "this
         * track has no ladder on it" rather than as an empty one.
         */
        public TrackState(int index, int lateralStart, int lateralEnd,
                          int preferredSquads, int effectiveSquads,
                          int effectiveLiveMembers, float friendlyBodyProgress,
                          float friendlyLeadProgress,
                          float knownHostileFrontProgress,
                          int knownHostileContacts, float friendlyPressure,
                          float knownHostilePressure, int targetZoneId,
                          int responderCap) {
            this(index, lateralStart, lateralEnd, preferredSquads,
                    effectiveSquads, effectiveLiveMembers,
                    friendlyBodyProgress, friendlyLeadProgress,
                    knownHostileFrontProgress, knownHostileContacts,
                    friendlyPressure, knownHostilePressure, targetZoneId,
                    responderCap, -1, -1, -1);
        }

        /**
         * A picture from a command that does not bound its per-track response,
         * which is every marine-perspective one: {@code -1} reads as "no cap
         * published" rather than as a cap of nothing.
         */
        public TrackState(int index, int lateralStart, int lateralEnd,
                          int preferredSquads, int effectiveSquads,
                          int effectiveLiveMembers, float friendlyBodyProgress,
                          float friendlyLeadProgress,
                          float knownHostileFrontProgress,
                          int knownHostileContacts, float friendlyPressure,
                          float knownHostilePressure, int targetZoneId) {
            this(index, lateralStart, lateralEnd, preferredSquads,
                    effectiveSquads, effectiveLiveMembers,
                    friendlyBodyProgress, friendlyLeadProgress,
                    knownHostileFrontProgress, knownHostileContacts,
                    friendlyPressure, knownHostilePressure, targetZoneId, -1);
        }

        /**
         * How much of this track's ladder the marines hold, as places rather
         * than ground: {@code -1} where the track has no ladder to score.
         */
        public float chainProgress() {
            if (chainLinks <= 0 || chainLinksHeld < 0) return -1f;
            return Math.min(1f, (float) chainLinksHeld / chainLinks);
        }
    }

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
            int membersInTargetZone,
            int membersInTargetPortal,
            boolean underFireRecently,
            boolean moraleBroken,
            String currentGoal,
            String currentAction,
            int movingMembers,
            int coveredFromPrimaryMembers,
            int primaryEngageableMembers,
            int primaryEngageableFireTeams,
            String contactPosture,
            String contactDoctrine,
            String contactInitiative,
            int coolingDownMembers) {

        public SquadState(int squadId, int aliveMembers, float centroidX,
                          float centroidY, int currentZoneId,
                          String executionSuspension, boolean localContact,
                          int activePathMembers) {
            this(squadId, aliveMembers, centroidX, centroidY, currentZoneId,
                    executionSuspension, localContact, activePathMembers, 0,
                    0, false, false, null, null, 0, -1, 0, 0,
                    null, null, null, 0);
        }

        public SquadState(int squadId, int aliveMembers, float centroidX,
                          float centroidY, int currentZoneId,
                          String executionSuspension, boolean localContact) {
            this(squadId, aliveMembers, centroidX, centroidY, currentZoneId,
                    executionSuspension, localContact, 0, 0,
                    0, false, false, null, null, 0, -1, 0, 0,
                    null, null, null, 0);
        }

        public SquadState(int squadId, int aliveMembers, float centroidX,
                          float centroidY, int currentZoneId,
                          String executionSuspension, boolean localContact,
                          int activePathMembers, int membersInTargetZone) {
            this(squadId, aliveMembers, centroidX, centroidY, currentZoneId,
                    executionSuspension, localContact, activePathMembers,
                    membersInTargetZone, 0, false, false, null, null,
                    0, -1, 0, 0, null, null, null, 0);
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
            boolean distantCaptureDeferred,
            int targetLane,
            int targetLink) {

        public SquadDirective(int squadId, int preferredTrack,
                              int effectiveTrack, AssignmentReason reason,
                              AssignmentKind assignmentKind, int targetZoneId,
                              int targetCellX, int targetCellY,
                              int markerCellX, int markerCellY,
                              boolean distantCaptureDeferred) {
            this(squadId, preferredTrack, effectiveTrack, reason,
                    assignmentKind, targetZoneId, targetCellX, targetCellY,
                    markerCellX, markerCellY, distantCaptureDeferred, -1, -1);
        }

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
                    targetCellY, markerCellX, markerCellY, true,
                    targetLane, targetLink);
        }

        /**
         * The same order, keyed to the place on the lane it is about.
         *
         * <p>A track index says which third of the map a squad is working in;
         * a lane and a link say <em>what it is being sent to take</em>, which
         * is what the report has to be able to say once progress is measured
         * in places held. {@code -1} for an order about no place at all — a
         * settlement compound, or open ground.
         */
        public SquadDirective withChainTarget(int lane, int link) {
            return new SquadDirective(squadId, preferredTrack, effectiveTrack,
                    reason, assignmentKind, targetZoneId, targetCellX,
                    targetCellY, markerCellX, markerCellY,
                    distantCaptureDeferred, lane, link);
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
