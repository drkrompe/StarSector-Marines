package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/** Immutable command-authored explanation of an Assault area search. */
public record AssaultSearchSnapshot(
        int tick,
        int influenceTick,
        Faction perspective,
        Phase phase,
        int searchPass,
        List<SectorState> sectors,
        List<SquadState> squads,
        List<SquadDirective> directives) {

    public enum Phase { SEARCH, CONVERGE, RECHECK }

    public enum SectorStatus { SEARCHING, SUSPECTED, ACTIVE, SEARCHED }

    public enum AssignmentReason {
        SECTOR_SEARCH_PRESERVED,
        SECTOR_SEARCH_ASSIGNED,
        ACTIVE_CONTACT_REINFORCEMENT,
        SUSPECTED_CONTACT_REINFORCEMENT,
        SECTOR_RECHECK_ASSIGNED,
        EXTERNAL_OWNERSHIP_PRESERVED,
        NO_REACHABLE_SECTOR
    }

    public record SectorState(
            int index,
            int minCellX,
            int minCellY,
            int width,
            int height,
            SectorStatus status,
            int visitedLegs,
            int totalLegs,
            int believedContacts,
            int freshestContactTick,
            int assignedSquads,
            int leadTargetCellX,
            int leadTargetCellY) { }

    public record SquadState(
            int squadId,
            int aliveMembers,
            float centroidX,
            float centroidY,
            int currentZoneId,
            String executionSuspension,
            boolean localContact) { }

    public record SquadDirective(
            int squadId,
            int sectorIndex,
            AssignmentReason reason,
            AssignmentKind assignmentKind,
            int targetCellX,
            int targetCellY) { }

    public AssaultSearchSnapshot {
        sectors = List.copyOf(sectors);
        squads = List.copyOf(squads);
        directives = List.copyOf(directives);
    }

    public SectorState sector(int index) {
        for (SectorState sector : sectors) {
            if (sector.index() == index) return sector;
        }
        return null;
    }

    public SquadDirective directiveFor(int squadId) {
        for (SquadDirective directive : directives) {
            if (directive.squadId() == squadId) return directive;
        }
        return null;
    }

    /** Replaces stability-held proposal rows with the arbiter's effective order. */
    public AssaultSearchSnapshot reconcileStableDirectives(
            CommanderSnapshot<?> committed, AssaultSearchSnapshot prior,
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
            ObjectiveAssignment assignment = result.assignment();
            reconciled.add(new SquadDirective(planned.squadId(),
                    previous != null ? previous.sectorIndex()
                            : planned.sectorIndex(),
                    AssignmentReason.valueOf(result.reason()),
                    assignment != null ? assignment.kind() : null,
                    assignment != null ? assignment.targetCellX() : -1,
                    assignment != null ? assignment.targetCellY() : -1));
        }
        return new AssaultSearchSnapshot(tick, influenceTick, perspective,
                phase, searchPass, sectors, squads, reconciled);
    }

    public static AssaultSearchSnapshot empty(Faction perspective) {
        return new AssaultSearchSnapshot(-1, -1, perspective, Phase.SEARCH, 1,
                List.of(), List.of(), List.of());
    }
}
