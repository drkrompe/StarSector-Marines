package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

import java.util.List;

/** Immutable command-authored explanation of the current Conquest front. */
public record ConquestFrontSnapshot(
        int tick,
        int influenceTick,
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
        NO_ACTIONABLE_TRACK_TARGET
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
            int targetZoneId) { }

    public ConquestFrontSnapshot {
        tracks = List.copyOf(tracks);
        directives = List.copyOf(directives);
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

    public static ConquestFrontSnapshot empty(TraversalAxis axis) {
        return new ConquestFrontSnapshot(-1, -1, axis, Phase.LANE_ADVANCE,
                -1, -1, null, List.of(), List.of());
    }
}
