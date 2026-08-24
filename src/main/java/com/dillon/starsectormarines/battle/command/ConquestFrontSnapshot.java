package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

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
        List<SquadDirective> directives) {

    public enum Phase { LANE_ADVANCE, FRONT_ADJUST, KEEP_CONVERGENCE }

    public enum AssignmentReason {
        GARRISON_HOLD,
        COMPOUND_CAPTURE_PRESERVED,
        COMPOUND_CAPTURE_UNCONTESTED,
        COMPOUND_ASSAULT_ADJACENT,
        TRACK_ADVANCE,
        ADJACENT_TRACK_SUPPORT,
        KEEP_APPROACH,
        NO_ACTIONABLE_TRACK_TARGET,
        DEFENDER_GARRISON_HOLD,
        DEFENDER_LOCAL_CONTACT,
        DEFENDER_TRACK_RESPONSE,
        DEFENDER_ADJACENT_TRACK_RESPONSE,
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

    public record SquadDirective(
            int squadId,
            int preferredTrack,
            int effectiveTrack,
            AssignmentReason reason,
            AssignmentKind assignmentKind,
            int targetZoneId,
            int targetCellX,
            int targetCellY) {

        public SquadDirective(int squadId, int preferredTrack,
                              int effectiveTrack, AssignmentReason reason,
                              AssignmentKind assignmentKind, int targetZoneId) {
            this(squadId, preferredTrack, effectiveTrack, reason,
                    assignmentKind, targetZoneId, -1, -1);
        }
    }

    public ConquestFrontSnapshot {
        tracks = List.copyOf(tracks);
        directives = List.copyOf(directives);
    }

    /** Back-compatible marine-perspective constructor for fixtures and older callers. */
    public ConquestFrontSnapshot(int tick, int influenceTick,
                                 TraversalAxis axis, Phase phase,
                                 int remainingCompounds, int keepZoneId,
                                 CompoundState keepState,
                                 List<TrackState> tracks,
                                 List<SquadDirective> directives) {
        this(tick, influenceTick, Faction.MARINE, axis, phase,
                remainingCompounds, keepZoneId, keepState, tracks, directives);
    }

    public SquadDirective directiveFor(int squadId) {
        for (SquadDirective directive : directives) {
            if (directive.squadId() == squadId) return directive;
        }
        return null;
    }

    public TrackState track(int index) {
        for (TrackState track : tracks) {
            if (track.index() == index) return track;
        }
        return null;
    }

    public static ConquestFrontSnapshot empty(Faction perspective,
                                               TraversalAxis axis) {
        return new ConquestFrontSnapshot(-1, -1, perspective, axis, Phase.LANE_ADVANCE,
                -1, -1, null, List.of(), List.of());
    }

    public static ConquestFrontSnapshot empty(TraversalAxis axis) {
        return empty(Faction.MARINE, axis);
    }
}
