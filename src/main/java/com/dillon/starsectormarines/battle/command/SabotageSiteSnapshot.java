package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/** Immutable command-authored explanation of Sabotage site task groups. */
public record SabotageSiteSnapshot(
        int tick,
        int influenceTick,
        Faction perspective,
        Phase phase,
        List<SiteState> sites,
        List<SquadState> squads,
        List<SquadDirective> directives) {

    public enum Phase { SITE_APPROACH, PLANTING, KIT_RECOVERY, COMPLETE }

    public enum GroupRole {
        PLANTER, KIT_RETRIEVER, SECURITY, REINFORCING, EXTERNAL, UNASSIGNED
    }

    public enum AssignmentReason {
        PLANTER_OBJECTIVE_PRESERVED,
        KIT_RECOVERY_PRESERVED,
        SITE_SECURITY_PRESERVED,
        SITE_SECURITY_ASSIGNED,
        SITE_REINFORCEMENT_ASSIGNED,
        EXTERNAL_OWNERSHIP_PRESERVED,
        NO_REACHABLE_SITE,
        ALL_SITES_COMPLETE
    }

    /** Why an unfinished site currently has, or lacks, planting capability. */
    public enum GroupReason {
        COMPLETE,
        PLANTER_ACTIVE,
        KIT_RECOVERY_ASSIGNED,
        KIT_RECOVERY_UNSUPPORTED,
        AWAITING_PLANTER
    }

    public record SiteState(
            int index,
            String id,
            String name,
            int cellX,
            int cellY,
            int zoneId,
            float progress,
            float plantDuration,
            boolean planterOnSite,
            boolean complete,
            int activeKitDrops,
            int unclaimedKitDrops,
            GroupReason groupReason,
            int planterSquads,
            int retrieverSquads,
            int securitySquads,
            int liveMembers,
            float friendlyPressure,
            float knownHostilePressure) { }

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
            int siteIndex,
            GroupRole groupRole,
            AssignmentReason reason,
            AssignmentKind assignmentKind,
            int targetZoneId,
            int markerCellX,
            int markerCellY) { }

    public SabotageSiteSnapshot {
        sites = List.copyOf(sites);
        squads = List.copyOf(squads);
        directives = List.copyOf(directives);
    }

    public SquadDirective directiveFor(int squadId) {
        for (SquadDirective directive : directives) {
            if (directive.squadId() == squadId) return directive;
        }
        return null;
    }

    public SiteState site(int index) {
        for (SiteState site : sites) {
            if (site.index() == index) return site;
        }
        return null;
    }

    /** Replaces stability-held proposal rows with the arbiter's effective order. */
    public SabotageSiteSnapshot reconcileStableDirectives(
            CommanderSnapshot<?> committed, SabotageSiteSnapshot prior,
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
            int siteIndex = previous != null ? previous.siteIndex()
                    : planned.siteIndex();
            SiteState site = site(siteIndex);
            AssignmentReason reason = AssignmentReason.valueOf(result.reason());
            reconciled.add(new SquadDirective(planned.squadId(), siteIndex,
                    previous != null ? previous.groupRole() : planned.groupRole(),
                    reason, assignment != null ? assignment.kind() : null,
                    assignment != null ? assignment.targetZoneId() : -1,
                    site != null ? site.cellX() : planned.markerCellX(),
                    site != null ? site.cellY() : planned.markerCellY()));
        }
        return new SabotageSiteSnapshot(tick, influenceTick, perspective, phase,
                sites, squads, reconciled);
    }

    public static SabotageSiteSnapshot empty(Faction perspective) {
        return new SabotageSiteSnapshot(-1, -1, perspective,
                Phase.SITE_APPROACH, List.of(), List.of(), List.of());
    }
}
