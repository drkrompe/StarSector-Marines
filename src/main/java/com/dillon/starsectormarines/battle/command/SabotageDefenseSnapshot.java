package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/** Defender-perspective explanation of Sabotage site coverage and response. */
public record SabotageDefenseSnapshot(
        int tick,
        int influenceTick,
        Faction perspective,
        Phase phase,
        int mobilePool,
        int reserveCount,
        List<SiteState> sites,
        List<SquadDirective> directives) {

    public enum Phase { ROUTINE_SECURITY, ALARM_RESPONSE, REDISTRIBUTION, COMPLETE }

    public enum Role { AUTHORED_POST, ROUTINE_SECURITY, ALARM_RESPONDER, RESERVE, EXTERNAL }

    public enum Reason {
        AUTHORED_POST_PRESERVED,
        EXTERNAL_OWNERSHIP_PRESERVED,
        ROUTINE_SITE_COVERAGE,
        SITE_ALARM_RESPONSE,
        BELIEVED_SITE_THREAT_RESPONSE,
        MOBILE_RESERVE_HELD,
        NO_REACHABLE_SITE,
        ALL_SITES_COMPLETE
    }

    public record SiteState(
            int index,
            String id,
            String name,
            int cellX,
            int cellY,
            int zoneId,
            boolean complete,
            boolean alarmActive,
            int alarmRaisedTick,
            int alarmExpiresTick,
            int routineSquads,
            int respondingSquads,
            int liveMembers,
            float friendlyPressure,
            float knownHostilePressure) { }

    public record SquadDirective(
            int squadId,
            int siteIndex,
            Role role,
            Reason reason,
            AssignmentKind assignmentKind,
            int markerCellX,
            int markerCellY) { }

    public SabotageDefenseSnapshot {
        sites = List.copyOf(sites);
        directives = List.copyOf(directives);
    }

    public SquadDirective directiveFor(int squadId) {
        for (SquadDirective directive : directives) {
            if (directive.squadId() == squadId) return directive;
        }
        return null;
    }

    public SiteState site(int index) {
        for (SiteState site : sites) if (site.index() == index) return site;
        return null;
    }

    public SabotageDefenseSnapshot reconcileStableDirectives(
            CommanderSnapshot<?> committed, SabotageDefenseSnapshot prior,
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
                    previous != null ? previous.siteIndex() : planned.siteIndex(),
                    previous != null ? previous.role() : planned.role(),
                    Reason.valueOf(result.reason()),
                    assignment != null ? assignment.kind() : null,
                    assignment != null ? assignment.targetCellX()
                            : planned.markerCellX(),
                    assignment != null ? assignment.targetCellY()
                            : planned.markerCellY()));
        }
        return new SabotageDefenseSnapshot(tick, influenceTick, perspective,
                phase, mobilePool, reserveCount, sites, rows);
    }

    public static SabotageDefenseSnapshot empty() {
        return new SabotageDefenseSnapshot(-1, -1, Faction.DEFENDER,
                Phase.ROUTINE_SECURITY, 0, 0, List.of(), List.of());
    }
}
