package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/** Immutable defender explanation for Assault strongpoints and reserve response. */
public record AssaultDefenseSnapshot(
        int tick,
        int influenceTick,
        Faction perspective,
        Phase phase,
        int mobilePool,
        int reserveCount,
        List<AreaState> areas,
        List<StrongpointState> strongpoints,
        List<SquadState> squads,
        List<SquadDirective> directives) {

    public enum Phase { AREA_SECURITY, REPORTED_CONTACT_RESPONSE }
    public enum ReportState { QUIET, SUSPECTED, ACTIVE }
    public enum Role { AUTHORED_POST, ROUTINE_SECURITY, RESPONDER, RESERVE, EXTERNAL }
    public enum Reason {
        AUTHORED_POST_PRESERVED,
        EXTERNAL_OWNERSHIP_PRESERVED,
        ROUTINE_AREA_COVERAGE,
        ACTIVE_CONTACT_RESPONSE,
        SUSPECTED_CONTACT_RESPONSE,
        MOBILE_RESERVE_HELD,
        NO_REACHABLE_AREA
    }

    public record AreaState(
            int index,
            int minCellX,
            int minCellY,
            int width,
            int height,
            int priority,
            int strongpoints,
            int garrisonSquads,
            int routineSquads,
            int respondingSquads,
            ReportState reportState,
            int believedContacts,
            int freshestContactTick,
            int reportExpiresTick,
            float friendlyPressure,
            float knownHostilePressure,
            int leadRallyCellX,
            int leadRallyCellY) { }

    public record StrongpointState(
            int index,
            String kind,
            int areaIndex,
            int anchorCellX,
            int anchorCellY,
            int rallyCellX,
            int rallyCellY,
            int zoneId,
            int priority) { }

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
            int areaIndex,
            Role role,
            Reason reason,
            AssignmentKind assignmentKind,
            int markerCellX,
            int markerCellY) { }

    public AssaultDefenseSnapshot {
        areas = List.copyOf(areas);
        strongpoints = List.copyOf(strongpoints);
        squads = List.copyOf(squads);
        directives = List.copyOf(directives);
    }

    public AreaState area(int index) {
        for (AreaState area : areas) if (area.index() == index) return area;
        return null;
    }

    public SquadDirective directiveFor(int squadId) {
        for (SquadDirective directive : directives) {
            if (directive.squadId() == squadId) return directive;
        }
        return null;
    }

    public AssaultDefenseSnapshot reconcileStableDirectives(
            CommanderSnapshot<?> committed, AssaultDefenseSnapshot prior,
            String strategy) {
        List<SquadDirective> rows = new ArrayList<>(directives.size());
        for (SquadDirective planned : directives) {
            CommandDirective result = committed.directiveFor(planned.squadId());
            if (result == null || result.status() != CommandDirective.Status.RETAINED
                    || !strategy.equals(result.issuer())
                    || !result.dispositionReason().startsWith("stable through tick")) {
                rows.add(planned);
                continue;
            }
            SquadDirective previous = prior != null
                    ? prior.directiveFor(planned.squadId()) : null;
            ObjectiveAssignment assignment = result.assignment();
            rows.add(new SquadDirective(planned.squadId(),
                    previous != null ? previous.areaIndex() : planned.areaIndex(),
                    previous != null ? previous.role() : planned.role(),
                    retainedReason(result.reason(), previous, planned),
                    assignment != null ? assignment.kind() : null,
                    assignment != null ? assignment.targetCellX()
                            : planned.markerCellX(),
                    assignment != null ? assignment.targetCellY()
                            : planned.markerCellY()));
        }
        int[] garrisons = new int[areas.size()];
        int[] routine = new int[areas.size()];
        int[] responders = new int[areas.size()];
        int[] leadX = new int[areas.size()];
        int[] leadY = new int[areas.size()];
        java.util.Arrays.fill(leadX, -1);
        java.util.Arrays.fill(leadY, -1);
        int reconciledReserve = 0;
        for (SquadDirective row : rows) {
            if (row.role() == Role.RESERVE) reconciledReserve++;
            int area = row.areaIndex();
            if (area < 0 || area >= areas.size()) continue;
            if (row.role() == Role.AUTHORED_POST) garrisons[area]++;
            if (row.role() == Role.ROUTINE_SECURITY) routine[area]++;
            if (row.role() == Role.RESPONDER) responders[area]++;
            if (leadX[area] < 0 && row.markerCellX() >= 0) {
                leadX[area] = row.markerCellX();
                leadY[area] = row.markerCellY();
            }
        }
        List<AreaState> reconciledAreas = new ArrayList<>(areas.size());
        for (AreaState area : areas) {
            int index = area.index();
            reconciledAreas.add(new AreaState(index, area.minCellX(),
                    area.minCellY(), area.width(), area.height(),
                    area.priority(), area.strongpoints(), garrisons[index],
                    routine[index], responders[index], area.reportState(),
                    area.believedContacts(), area.freshestContactTick(),
                    area.reportExpiresTick(), area.friendlyPressure(),
                    area.knownHostilePressure(), leadX[index], leadY[index]));
        }
        return new AssaultDefenseSnapshot(tick, influenceTick, perspective,
                phase, mobilePool, reconciledReserve, reconciledAreas,
                strongpoints, squads, rows);
    }

    private static Reason retainedReason(String reason,
                                         SquadDirective previous,
                                         SquadDirective planned) {
        if (reason != null) {
            try {
                return Reason.valueOf(reason);
            } catch (IllegalArgumentException ignored) {
                // Explicit handoffs may retain human-readable provenance.
            }
        }
        return previous != null ? previous.reason() : planned.reason();
    }

    public static AssaultDefenseSnapshot empty() {
        return new AssaultDefenseSnapshot(-1, -1, Faction.DEFENDER,
                Phase.AREA_SECURITY, 0, 0, List.of(), List.of(), List.of(),
                List.of());
    }
}
