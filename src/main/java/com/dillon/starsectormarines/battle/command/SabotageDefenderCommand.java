package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.SabotageDefenseSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.SabotageDefenseSnapshot.Reason;
import com.dillon.starsectormarines.battle.command.SabotageDefenseSnapshot.Role;
import com.dillon.starsectormarines.battle.command.SabotageDefenseSnapshot.SiteState;
import com.dillon.starsectormarines.battle.command.SabotageDefenseSnapshot.SquadDirective;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Defender-side named-site security with fixed posts and a bounded mobile reserve. */
public final class SabotageDefenderCommand implements AutonomousMissionCommand<
        SabotageDefenderCommandFrame, SabotageDefenseSnapshot> {

    static final int MAX_RESPONDERS_PER_SITE = 2;
    private static final float BELIEVED_THREAT_THRESHOLD = 0.1f;

    private final Set<Integer> initialMobileSquads = new TreeSet<>();
    private final Map<Integer, Integer> squadSite = new HashMap<>();
    private volatile SabotageDefenseSnapshot defenseSnapshot =
            SabotageDefenseSnapshot.empty();

    private static final int[][] SITE_RALLY_OFFSETS = {
            {0, -3}, {3, 0}, {0, 3}, {-3, 0},
            {2, -2}, {2, 2}, {-2, 2}, {-2, -2}, {0, 0}
    };

    private record Rally(int x, int y, int route) { }

    public SabotageDefenderCommand(Set<Integer> mobileSquadIds) {
        if (mobileSquadIds != null) initialMobileSquads.addAll(mobileSquadIds);
    }

    public SabotageDefenseSnapshot defenseSnapshot() { return defenseSnapshot; }

    @Override public Faction faction() { return Faction.DEFENDER; }
    @Override public String strategyId() { return "sabotage-defender"; }

    @Override
    public CommandPlan<SabotageDefenseSnapshot> plan(
            SabotageDefenderCommandFrame frame) {
        List<SabotageDefenderCommandFacts.Site> activeSites = frame.facts().sites()
                .stream().filter(site -> !site.complete()).toList();
        Map<Integer, SquadDirective> directives = new LinkedHashMap<>();
        List<CommandSquadState> candidates = new ArrayList<>();

        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            int nearest = nearestSite(squad, frame.facts().sites(), frame);
            CommandDirective incumbent = squad.directive();
            boolean handedToStrategy = incumbent != null
                    && incumbent.authority() == CommandAuthority.MISSION_COMMAND
                    && strategyId().equals(incumbent.issuer());
            if (!initialMobileSquads.contains(squad.squadId())
                    && !handedToStrategy) {
                Role role = squad.role() == UnitRole.GARRISON
                        ? Role.AUTHORED_POST : Role.EXTERNAL;
                Reason reason = role == Role.AUTHORED_POST
                        ? Reason.AUTHORED_POST_PRESERVED
                        : Reason.EXTERNAL_OWNERSHIP_PRESERVED;
                directives.put(squad.squadId(), directive(squad, nearest,
                        role, reason, null, frame));
                continue;
            }
            if (incumbent != null
                    && incumbent.authority().priority()
                    > CommandAuthority.MISSION_COMMAND.priority()) {
                directives.put(squad.squadId(), directive(squad, nearest,
                        Role.EXTERNAL, Reason.EXTERNAL_OWNERSHIP_PRESERVED,
                        null, frame));
                continue;
            }
            candidates.add(squad);
        }

        List<CommandSquadState> available = new ArrayList<>(candidates);
        Set<Integer> selected = new HashSet<>();
        Map<Integer, Integer> routineCount = new HashMap<>();
        Map<Integer, Integer> responseCount = new HashMap<>();

        // One routine patrol per reachable unfinished site before any site gets
        // a second squad. Sticky ownership wins ties to avoid command churn.
        for (SabotageDefenderCommandFacts.Site site : activeSites) {
            CommandSquadState chosen = chooseSquad(available, selected, site,
                    frame, true, 0);
            if (chosen == null) continue;
            assign(chosen, site, Role.ROUTINE_SECURITY,
                    Reason.ROUTINE_SITE_COVERAGE, directives, selected,
                    frame, 0);
            routineCount.merge(site.index(), 1, Integer::sum);
        }

        List<SabotageDefenderCommandFacts.Site> threatened = activeSites.stream()
                .filter(site -> site.alarm().active()
                        || hostilePressure(site, frame) > BELIEVED_THREAT_THRESHOLD)
                .sorted(Comparator
                        .comparing((SabotageDefenderCommandFacts.Site site) ->
                                !site.alarm().active())
                        .thenComparing((SabotageDefenderCommandFacts.Site site) ->
                                -hostilePressure(site, frame))
                        .thenComparingInt(SabotageDefenderCommandFacts.Site::index))
                .toList();

        // First response pass spreads reserve across distinct threatened sites.
        for (SabotageDefenderCommandFacts.Site site : threatened) {
            assignResponder(site, available, selected, frame, directives,
                    responseCount);
        }
        // A severe site may receive one additional responder only after every
        // reachable threatened site had its first opportunity.
        for (SabotageDefenderCommandFacts.Site site : threatened) {
            while (responseCount.getOrDefault(site.index(), 0)
                    < MAX_RESPONDERS_PER_SITE) {
                if (!assignResponder(site, available, selected, frame,
                        directives, responseCount)) break;
            }
        }

        List<CommandProposal> proposals = new ArrayList<>();
        for (CommandSquadState squad : candidates) {
            SquadDirective directive = directives.get(squad.squadId());
            if (directive != null) {
                ObjectiveAssignment assignment = ObjectiveAssignment.defendSite(
                        squad.squadId(), directive.markerCellX(),
                        directive.markerCellY());
                proposals.add(CommandProposal.assign(assignment,
                        CommandAuthority.MISSION_COMMAND,
                        directive.reason().name(), stabilityBreak(squad,
                                assignment, frame)));
                continue;
            }
            Reason reason = activeSites.isEmpty()
                    ? Reason.ALL_SITES_COMPLETE : Reason.MOBILE_RESERVE_HELD;
            directives.put(squad.squadId(), directive(squad, -1,
                    Role.RESERVE, reason, null, frame));
            squadSite.remove(squad.squadId());
            if (squad.directive() != null
                    && strategyId().equals(squad.directive().issuer())) {
                proposals.add(CommandProposal.release(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND, reason.name(),
                        activeSites.isEmpty()
                                ? CommandStabilityBreak.OBJECTIVE_COMPLETED
                                : CommandStabilityBreak.CONTEXT_INVALIDATED));
            }
        }

        int reserve = candidates.size() - selected.size();
        Phase phase = activeSites.isEmpty() ? Phase.COMPLETE
                : !threatened.isEmpty() ? Phase.ALARM_RESPONSE
                : completedSiteCount(frame.facts()) > 0
                ? Phase.REDISTRIBUTION : Phase.ROUTINE_SECURITY;
        SabotageDefenseSnapshot detail = buildSnapshot(frame, phase,
                candidates.size(), reserve, directives, routineCount,
                responseCount);
        List<String> objectives = frame.facts().sites().stream()
                .map(site -> site.name() + "=" + (site.complete() ? "complete"
                        : site.alarm().active() ? "alarm" : "secure"))
                .toList();
        return new CommandPlan<>(faction(), strategyId(), phase.name(),
                frame.tick(), frame.influence() != null
                ? frame.influence().updatedTick() : -1,
                candidates.size(), reserve, objectives, proposals, detail);
    }

    @Override
    public CommanderSnapshot<SabotageDefenseSnapshot> reconcile(
            CommanderSnapshot<SabotageDefenseSnapshot> snapshot) {
        return snapshot.withDetail(snapshot.detail().reconcileStableDirectives(
                snapshot, defenseSnapshot, strategyId()));
    }

    @Override
    public void publish(CommanderSnapshot<SabotageDefenseSnapshot> snapshot) {
        defenseSnapshot = snapshot.detail();
    }

    private void assign(CommandSquadState squad,
                        SabotageDefenderCommandFacts.Site site, Role role,
                        Reason reason, Map<Integer, SquadDirective> directives,
                        Set<Integer> selected,
                        SabotageDefenderCommandFrame frame, int rallySlot) {
        Rally rally = rallyFor(squad, site, rallySlot, frame);
        if (rally == null) return;
        ObjectiveAssignment assignment = ObjectiveAssignment.defendSite(
                squad.squadId(), rally.x(), rally.y());
        directives.put(squad.squadId(), new SquadDirective(squad.squadId(),
                site.index(), role, reason, assignment.kind(), rally.x(),
                rally.y()));
        selected.add(squad.squadId());
        squadSite.put(squad.squadId(), site.index());
    }

    private boolean assignResponder(
            SabotageDefenderCommandFacts.Site site,
            List<CommandSquadState> available, Set<Integer> selected,
            SabotageDefenderCommandFrame frame,
            Map<Integer, SquadDirective> directives,
            Map<Integer, Integer> responseCount) {
        CommandSquadState chosen = chooseSquad(available, selected, site,
                frame, false, responseCount.getOrDefault(site.index(), 0) + 1);
        if (chosen == null) return false;
        Reason reason = site.alarm().active()
                ? Reason.SITE_ALARM_RESPONSE
                : Reason.BELIEVED_SITE_THREAT_RESPONSE;
        int rallySlot = responseCount.getOrDefault(site.index(), 0) + 1;
        assign(chosen, site, Role.ALARM_RESPONDER, reason, directives, selected,
                frame, rallySlot);
        responseCount.merge(site.index(), 1, Integer::sum);
        return true;
    }

    private CommandSquadState chooseSquad(List<CommandSquadState> candidates,
                                          Set<Integer> selected,
                                          SabotageDefenderCommandFacts.Site site,
                                          SabotageDefenderCommandFrame frame,
                                          boolean preferSticky, int rallySlot) {
        CommandSquadState best = null;
        long bestScore = Long.MAX_VALUE;
        for (CommandSquadState squad : candidates) {
            if (selected.contains(squad.squadId())) continue;
            Rally rally = rallyFor(squad, site, rallySlot, frame);
            if (rally == null) continue;
            long sticky = preferSticky
                    && Objects.equals(squadSite.get(squad.squadId()), site.index())
                    ? -1_000_000L : 0L;
            long score = sticky + rally.route();
            if (score < bestScore || (score == bestScore
                    && (best == null || squad.squadId() < best.squadId()))) {
                best = squad;
                bestScore = score;
            }
        }
        return best;
    }

    private static Rally rallyFor(CommandSquadState squad,
                                  SabotageDefenderCommandFacts.Site site,
                                  int slot,
                                  SabotageDefenderCommandFrame frame) {
        for (int i = 0; i < SITE_RALLY_OFFSETS.length; i++) {
            int[] offset = SITE_RALLY_OFFSETS[(slot + i)
                    % SITE_RALLY_OFFSETS.length];
            int x = site.cellX() + offset[0];
            int y = site.cellY() + offset[1];
            if (!frame.topology().inBounds(x, y)
                    || !frame.topology().isWalkable(x, y)) continue;
            int route = frame.topology().routeLength(squad.anchorCellX(),
                    squad.anchorCellY(), x, y);
            if (route != Integer.MAX_VALUE) return new Rally(x, y, route);
        }
        return null;
    }

    private CommandStabilityBreak stabilityBreak(
            CommandSquadState squad, ObjectiveAssignment assignment,
            SabotageDefenderCommandFrame frame) {
        CommandDirective incumbent = squad.directive();
        if (incumbent == null || incumbent.assignment() == null
                || !strategyId().equals(incumbent.issuer())
                || Objects.equals(incumbent.assignment(), assignment)) {
            return CommandStabilityBreak.NONE;
        }
        ObjectiveAssignment old = incumbent.assignment();
        for (SabotageDefenderCommandFacts.Site site : frame.facts().sites()) {
            if (site.complete() && ownsRally(site, old.targetCellX(),
                    old.targetCellY())) {
                return CommandStabilityBreak.OBJECTIVE_COMPLETED;
            }
        }
        return CommandStabilityBreak.CONTEXT_INVALIDATED;
    }

    private static boolean ownsRally(SabotageDefenderCommandFacts.Site site,
                                     int x, int y) {
        for (int[] offset : SITE_RALLY_OFFSETS) {
            if (site.cellX() + offset[0] == x
                    && site.cellY() + offset[1] == y) return true;
        }
        return false;
    }

    private static int nearestSite(CommandSquadState squad,
                                   List<SabotageDefenderCommandFacts.Site> sites,
                                   SabotageDefenderCommandFrame frame) {
        int best = -1;
        int bestRoute = Integer.MAX_VALUE;
        for (SabotageDefenderCommandFacts.Site site : sites) {
            int route = frame.topology().routeLength(squad.anchorCellX(),
                    squad.anchorCellY(), site.cellX(), site.cellY());
            if (route < bestRoute) { bestRoute = route; best = site.index(); }
        }
        return best;
    }

    private static SquadDirective directive(
            CommandSquadState squad, int siteIndex, Role role, Reason reason,
            ObjectiveAssignment assignment, SabotageDefenderCommandFrame frame) {
        SabotageDefenderCommandFacts.Site site = frame.facts().site(siteIndex);
        return new SquadDirective(squad.squadId(), siteIndex, role, reason,
                assignment != null ? assignment.kind() : null,
                site != null ? site.cellX() : -1,
                site != null ? site.cellY() : -1);
    }

    private static float hostilePressure(
            SabotageDefenderCommandFacts.Site site,
            SabotageDefenderCommandFrame frame) {
        CommanderInfluenceSnapshot influence = frame.influence();
        return influence != null
                ? influence.hostileAtWorld(site.cellX(), site.cellY()) : 0f;
    }

    private static int completedSiteCount(SabotageDefenderCommandFacts facts) {
        int count = 0;
        for (SabotageDefenderCommandFacts.Site site : facts.sites()) {
            if (site.complete()) count++;
        }
        return count;
    }

    private static SabotageDefenseSnapshot buildSnapshot(
            SabotageDefenderCommandFrame frame, Phase phase, int mobilePool,
            int reserve, Map<Integer, SquadDirective> directives,
            Map<Integer, Integer> routineCount,
            Map<Integer, Integer> responseCount) {
        CommanderInfluenceSnapshot influence = frame.influence();
        List<SiteState> sites = new ArrayList<>();
        for (SabotageDefenderCommandFacts.Site site : frame.facts().sites()) {
            int liveMembers = 0;
            for (CommandSquadState squad : frame.squads()) {
                SquadDirective directive = directives.get(squad.squadId());
                if (directive != null && directive.siteIndex() == site.index()) {
                    liveMembers += squad.aliveMembers();
                }
            }
            sites.add(new SiteState(site.index(), site.id(), site.name(),
                    site.cellX(), site.cellY(), site.zoneId(), site.complete(),
                    site.alarm().active(), site.alarm().raisedTick(),
                    site.alarm().expiresTick(),
                    routineCount.getOrDefault(site.index(), 0),
                    responseCount.getOrDefault(site.index(), 0), liveMembers,
                    influence != null ? influence.friendlyAtWorld(
                            site.cellX(), site.cellY()) : 0f,
                    hostilePressure(site, frame)));
        }
        return new SabotageDefenseSnapshot(frame.tick(), influence != null
                ? influence.updatedTick() : -1, frame.perspective(), phase,
                mobilePool, reserve, sites,
                new ArrayList<>(directives.values()));
    }
}
