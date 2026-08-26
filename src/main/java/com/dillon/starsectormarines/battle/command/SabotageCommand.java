package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.SabotageSiteSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.SabotageSiteSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.SabotageSiteSnapshot.SiteState;
import com.dillon.starsectormarines.battle.command.SabotageSiteSnapshot.SquadDirective;
import com.dillon.starsectormarines.battle.command.SabotageSiteSnapshot.SquadState;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Marine-side Sabotage commander. It organizes ordinary squads into sticky
 * security groups around the mission's named charge sites while leaving the
 * planter and kit-retriever roles under their dedicated unit-level planners.
 */
public final class SabotageCommand implements
        AutonomousMissionCommand<SabotageCommandFrame, SabotageSiteSnapshot> {

    private final Map<Integer, Integer> squadSite = new HashMap<>();
    private volatile SabotageSiteSnapshot siteSnapshot =
            SabotageSiteSnapshot.empty(Faction.MARINE);

    public SabotageSiteSnapshot siteSnapshot() {
        return siteSnapshot;
    }

    @Override
    public Faction faction() {
        return Faction.MARINE;
    }

    @Override
    public String strategyId() {
        return "sabotage-attacker";
    }

    @Override
    public CommandPlan<SabotageSiteSnapshot> plan(SabotageCommandFrame frame) {
        List<SabotageCommandFacts.Site> activeSites = frame.facts().sites().stream()
                .filter(site -> !site.complete() && site.zoneId() >= 0)
                .toList();
        Set<Integer> planterSquads = squadIds(frame.facts(), true);
        Set<Integer> retrieverSquads = squadIds(frame.facts(), false);
        Phase phase = phase(frame.facts(), activeSites, retrieverSquads);

        Map<Integer, Integer> siteLoad = new HashMap<>();
        Map<Integer, SquadDirective> directives = new LinkedHashMap<>();
        List<CommandProposal> proposals = new ArrayList<>();
        int commandPool = 0;

        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            commandPool++;
            CommandDirective incumbent = squad.directive();
            if (incumbent != null && incumbent.authority().priority()
                    > CommandAuthority.MISSION_COMMAND.priority()) {
                directives.put(squad.squadId(), directive(squad, -1,
                        AssignmentReason.EXTERNAL_OWNERSHIP_PRESERVED, null, frame));
                proposals.add(CommandProposal.retain(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        AssignmentReason.EXTERNAL_OWNERSHIP_PRESERVED.name()));
                continue;
            }

            int specialSite = siteForSpecialSquad(frame.facts(), squad.squadId(),
                    planterSquads.contains(squad.squadId()));
            if (planterSquads.contains(squad.squadId())) {
                AssignmentReason reason = AssignmentReason.PLANTER_OBJECTIVE_PRESERVED;
                directives.put(squad.squadId(), directive(squad, specialSite,
                        reason, null, frame));
                proposals.add(CommandProposal.claim(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND, reason.name(),
                        CommandStabilityBreak.CONTEXT_INVALIDATED));
                continue;
            }
            if (retrieverSquads.contains(squad.squadId())) {
                AssignmentReason reason = AssignmentReason.KIT_RECOVERY_PRESERVED;
                directives.put(squad.squadId(), directive(squad, specialSite,
                        reason, null, frame));
                proposals.add(CommandProposal.claim(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND, reason.name(),
                        CommandStabilityBreak.CONTEXT_INVALIDATED));
                continue;
            }

            SabotageCommandFacts.Site site = chooseSite(squad, activeSites,
                    siteLoad, frame);
            if (site == null) {
                AssignmentReason reason = activeSites.isEmpty()
                        ? AssignmentReason.ALL_SITES_COMPLETE
                        : AssignmentReason.NO_REACHABLE_SITE;
                directives.put(squad.squadId(), directive(squad, -1,
                        reason, null, frame));
                CommandStabilityBreak stabilityBreak = activeSites.isEmpty()
                        ? CommandStabilityBreak.OBJECTIVE_COMPLETED
                        : CommandStabilityBreak.TARGET_UNREACHABLE;
                proposals.add(releaseOrRetain(squad, reason, stabilityBreak));
                squadSite.remove(squad.squadId());
                continue;
            }

            Integer priorSite = squadSite.put(squad.squadId(), site.index());
            siteLoad.merge(site.index(), 1, Integer::sum);
            ObjectiveAssignment assignment = ObjectiveAssignment.clearZone(
                    squad.squadId(), site.zoneId());
            boolean preserved = priorSite != null && priorSite == site.index();
            AssignmentReason reason = preserved
                    ? AssignmentReason.SITE_SECURITY_PRESERVED
                    : siteLoad.get(site.index()) > 1
                    ? AssignmentReason.SITE_REINFORCEMENT_ASSIGNED
                    : AssignmentReason.SITE_SECURITY_ASSIGNED;
            directives.put(squad.squadId(), directive(squad, site.index(),
                    reason, assignment, frame));
            proposals.add(CommandProposal.assign(assignment,
                    CommandAuthority.MISSION_COMMAND, reason.name(),
                    stabilityBreak(squad, assignment, frame)));
        }

        SabotageSiteSnapshot detail = buildSnapshot(frame, phase, directives);
        List<String> objectives = frame.facts().sites().stream()
                .map(site -> site.name() + "="
                        + (site.complete() ? "complete" : Math.round(site.progress())
                        + "/" + Math.round(site.plantDuration())))
                .toList();
        return new CommandPlan<>(faction(), strategyId(), phase.name(), frame.tick(),
                frame.influence() != null ? frame.influence().updatedTick() : -1,
                commandPool, 0, objectives, proposals, detail);
    }

    @Override
    public CommanderSnapshot<SabotageSiteSnapshot> reconcile(
            CommanderSnapshot<SabotageSiteSnapshot> snapshot) {
        return snapshot.withDetail(snapshot.detail().reconcileStableDirectives(
                snapshot, siteSnapshot, strategyId()));
    }

    @Override
    public void publish(CommanderSnapshot<SabotageSiteSnapshot> snapshot) {
        siteSnapshot = snapshot.detail();
    }

    private SabotageCommandFacts.Site chooseSite(CommandSquadState squad,
                                                  List<SabotageCommandFacts.Site> active,
                                                  Map<Integer, Integer> loads,
                                                  SabotageCommandFrame frame) {
        Integer sticky = squadSite.get(squad.squadId());
        if (sticky != null) {
            for (SabotageCommandFacts.Site site : active) {
                if (site.index() == sticky && reachable(squad, site, frame)) return site;
            }
        }
        SabotageCommandFacts.Site best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (SabotageCommandFacts.Site site : active) {
            int route = routeLength(squad, site, frame);
            if (route == Integer.MAX_VALUE) continue;
            float hostile = frame.influence() != null
                    ? frame.influence().hostileAtWorld(site.cellX(), site.cellY()) : 0f;
            double score = loads.getOrDefault(site.index(), 0) * 10_000.0
                    + route - hostile * 4.0;
            if (score < bestScore || (score == bestScore
                    && (best == null || site.index() < best.index()))) {
                best = site;
                bestScore = score;
            }
        }
        return best;
    }

    private static int routeLength(CommandSquadState squad,
                                   SabotageCommandFacts.Site site,
                                   SabotageCommandFrame frame) {
        return frame.topology().routeLength(squad.anchorCellX(), squad.anchorCellY(),
                site.cellX(), site.cellY());
    }

    private static boolean reachable(CommandSquadState squad,
                                     SabotageCommandFacts.Site site,
                                     SabotageCommandFrame frame) {
        return routeLength(squad, site, frame) != Integer.MAX_VALUE;
    }

    private CommandStabilityBreak stabilityBreak(CommandSquadState squad,
                                                  ObjectiveAssignment assignment,
                                                  SabotageCommandFrame frame) {
        CommandDirective incumbent = squad.directive();
        if (incumbent == null || incumbent.assignment() == null
                || !strategyId().equals(incumbent.issuer())
                || Objects.equals(incumbent.assignment(), assignment)) {
            return CommandStabilityBreak.NONE;
        }
        SabotageCommandFacts.Site oldSite = siteByZone(frame.facts(),
                incumbent.assignment().targetZoneId());
        if (oldSite == null || oldSite.complete()) {
            return CommandStabilityBreak.OBJECTIVE_COMPLETED;
        }
        if (!frame.topology().reachable(squad.anchorCellX(), squad.anchorCellY(),
                oldSite.cellX(), oldSite.cellY())) {
            return CommandStabilityBreak.TARGET_UNREACHABLE;
        }
        return CommandStabilityBreak.NONE;
    }

    private static CommandProposal releaseOrRetain(CommandSquadState squad,
                                                    AssignmentReason reason,
                                                    CommandStabilityBreak stabilityBreak) {
        if (squad.assignment() != null) {
            return CommandProposal.release(squad.squadId(),
                    CommandAuthority.MISSION_COMMAND, reason.name(), stabilityBreak);
        }
        return CommandProposal.retain(squad.squadId(),
                CommandAuthority.MISSION_COMMAND, reason.name());
    }

    private static SquadDirective directive(CommandSquadState squad, int siteIndex,
                                             AssignmentReason reason,
                                             ObjectiveAssignment assignment,
                                             SabotageCommandFrame frame) {
        SabotageCommandFacts.Site site = frame.facts().site(siteIndex);
        return new SquadDirective(squad.squadId(), siteIndex, groupRole(reason), reason,
                assignment != null ? assignment.kind() : null,
                assignment != null ? assignment.targetZoneId() : -1,
                site != null ? site.cellX() : -1,
                site != null ? site.cellY() : -1);
    }

    private static SabotageSiteSnapshot.GroupRole groupRole(
            AssignmentReason reason) {
        return switch (reason) {
            case PLANTER_OBJECTIVE_PRESERVED ->
                    SabotageSiteSnapshot.GroupRole.PLANTER;
            case KIT_RECOVERY_PRESERVED ->
                    SabotageSiteSnapshot.GroupRole.KIT_RETRIEVER;
            case SITE_REINFORCEMENT_ASSIGNED ->
                    SabotageSiteSnapshot.GroupRole.REINFORCING;
            case SITE_SECURITY_PRESERVED, SITE_SECURITY_ASSIGNED ->
                    SabotageSiteSnapshot.GroupRole.SECURITY;
            case EXTERNAL_OWNERSHIP_PRESERVED ->
                    SabotageSiteSnapshot.GroupRole.EXTERNAL;
            case NO_REACHABLE_SITE, ALL_SITES_COMPLETE ->
                    SabotageSiteSnapshot.GroupRole.UNASSIGNED;
        };
    }

    private static SabotageSiteSnapshot buildSnapshot(
            SabotageCommandFrame frame, Phase phase,
            Map<Integer, SquadDirective> directives) {
        CommanderInfluenceSnapshot influence = frame.influence();
        List<SiteState> sites = new ArrayList<>();
        for (SabotageCommandFacts.Site site : frame.facts().sites()) {
            int security = 0;
            int liveMembers = 0;
            for (CommandSquadState squad : frame.squads()) {
                SquadDirective directive = directives.get(squad.squadId());
                if (directive != null && directive.siteIndex() == site.index()
                        && directive.assignmentKind() == AssignmentKind.CLEAR_ZONE) {
                    security++;
                    liveMembers += squad.aliveMembers();
                }
            }
            sites.add(new SiteState(site.index(), site.id(), site.name(), site.cellX(),
                    site.cellY(), site.zoneId(), site.progress(), site.plantDuration(),
                    site.planterOnSite(), site.complete(), site.activeKitDrops(),
                    site.unclaimedKitDrops(), groupReason(site),
                    site.planterSquadIds().size(),
                    site.retrieverSquadIds().size(), security, liveMembers,
                    influence != null ? influence.friendlyAtWorld(
                            site.cellX(), site.cellY()) : 0f,
                    influence != null ? influence.hostileAtWorld(
                            site.cellX(), site.cellY()) : 0f));
        }
        List<SquadState> squads = frame.squads().stream()
                .map(squad -> new SquadState(squad.squadId(), squad.aliveMembers(),
                        squad.centroidX(), squad.centroidY(), squad.currentZoneId(),
                        squad.executionSuspension(), squad.localContact()))
                .toList();
        return new SabotageSiteSnapshot(frame.tick(),
                influence != null ? influence.updatedTick() : -1,
                frame.perspective(), phase, sites, squads,
                new ArrayList<>(directives.values()));
    }

    private static SabotageSiteSnapshot.GroupReason groupReason(
            SabotageCommandFacts.Site site) {
        if (site.complete()) return SabotageSiteSnapshot.GroupReason.COMPLETE;
        if (!site.planterSquadIds().isEmpty()) {
            return SabotageSiteSnapshot.GroupReason.PLANTER_ACTIVE;
        }
        if (site.unclaimedKitDrops() > 0) {
            return SabotageSiteSnapshot.GroupReason.KIT_RECOVERY_UNSUPPORTED;
        }
        if (!site.retrieverSquadIds().isEmpty()) {
            return SabotageSiteSnapshot.GroupReason.KIT_RECOVERY_ASSIGNED;
        }
        return SabotageSiteSnapshot.GroupReason.AWAITING_PLANTER;
    }

    private static Set<Integer> squadIds(SabotageCommandFacts facts, boolean planter) {
        Set<Integer> ids = new HashSet<>();
        for (SabotageCommandFacts.Site site : facts.sites()) {
            ids.addAll(planter ? site.planterSquadIds() : site.retrieverSquadIds());
        }
        return ids;
    }

    private static int siteForSpecialSquad(SabotageCommandFacts facts, int squadId,
                                           boolean planter) {
        for (SabotageCommandFacts.Site site : facts.sites()) {
            List<Integer> ids = planter ? site.planterSquadIds()
                    : site.retrieverSquadIds();
            if (ids.contains(squadId)) return site.index();
        }
        return -1;
    }

    private static SabotageCommandFacts.Site siteByZone(SabotageCommandFacts facts,
                                                        int zoneId) {
        for (SabotageCommandFacts.Site site : facts.sites()) {
            if (site.zoneId() == zoneId) return site;
        }
        return null;
    }

    private static Phase phase(SabotageCommandFacts facts,
                               List<SabotageCommandFacts.Site> activeSites,
                               Set<Integer> retrieverSquads) {
        if (activeSites.isEmpty()) return Phase.COMPLETE;
        if (!retrieverSquads.isEmpty()) return Phase.KIT_RECOVERY;
        for (SabotageCommandFacts.Site site : facts.sites()) {
            if (!site.complete() && site.activeKitDrops() > 0) {
                return Phase.KIT_RECOVERY;
            }
        }
        for (SabotageCommandFacts.Site site : facts.sites()) {
            if (!site.complete() && (site.planterOnSite() || site.progress() > 0f)) {
                return Phase.PLANTING;
            }
        }
        return Phase.SITE_APPROACH;
    }

}
