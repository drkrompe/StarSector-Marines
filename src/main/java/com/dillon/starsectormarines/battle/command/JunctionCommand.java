package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.JunctionCompound;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mission-specific duties over frozen public territory and own-force reports only. The raiders' reserve and road column
 * man the places near their waiting posts while they wait, within the story share ({@link StoryGarrisons}); once
 * committed they march as authored.
 */
public final class JunctionCommand implements AutonomousMissionCommand<ConquestCommandFrame, JunctionCommand.Picture>,
        ReactiveDefenceSource {
    /** @param reserveArrived whether the reserve has reached its approach to the first gain: the first blow's go */
    public record Picture(int targetSite, boolean reserveCommitted, boolean reserveArrived, List<Integer> commandedSquads) {
        public Picture { commandedSquads = List.copyOf(commandedSquads); }
    }
    /** Within this many cells of its target's capture cell the road column stops marching and holds it. */
    private static final double ROAD_ARRIVAL = 10;
    private final Faction faction;
    private final int reserveX, reserveY, roadX, roadY;
    private final JunctionTactics tactics = new JunctionTactics();
    private volatile Picture picture;
    private boolean reserveOnApproach, roadOnTarget, classified;
    /** Defender squads that started at the Rill road post rather than in the maintenance building. */
    private final Set<Integer> roadColumn = new HashSet<>();
    private final StoryGarrisons garrisons = new StoryGarrisons();
    public JunctionCommand(Faction faction, int reserveX, int reserveY, int roadX, int roadY) {
        if (faction != Faction.MARINE && faction != Faction.ALLY && faction != Faction.DEFENDER)
            throw new IllegalArgumentException("Junction command requires a fighting faction");
        this.faction = faction;
        this.reserveX = reserveX; this.reserveY = reserveY;
        this.roadX = roadX; this.roadY = roadY;
    }
    public static String issuer(Faction faction) { return "junction:" + faction; }
    @Override public Faction faction() { return faction; }
    @Override public String strategyId() { return issuer(faction); }
    public Picture picture() { return picture; }
    @Override public void publish(CommanderSnapshot<Picture> snapshot) { picture = snapshot.detail(); }
    @Override public void afterCommit(CommandEffects effects) { garrisons.afterCommit(effects, faction); }
    @Override public Map<String, Integer> reactiveDefenceFigures() { return garrisons.figures(); }

    @Override public CommandPlan<Picture> plan(ConquestCommandFrame frame) {
        var sites = frame.facts().compounds();
        var states = sites.stream().map(ConquestCommandFacts.Compound::state).toList();
        tactics.observe(states, frame.tick());
        if (faction == Faction.DEFENDER && !classified) {
            // Own-force reports only: each squad belongs to whichever waiting post it started nearer.
            for (var squad : frame.squads())
                if (squad.aliveMembers() > 0 && Math.hypot(squad.centroidX() - roadX, squad.centroidY() - roadY)
                        < Math.hypot(squad.centroidX() - reserveX, squad.centroidY() - reserveY)) roadColumn.add(squad.squadId());
            classified = true;
        }
        if (faction == Faction.DEFENDER && tactics.reserveCommitted())
            for (var squad : frame.squads()) {
                var directive = squad.directive();
                if (directive == null || !strategyId().equals(directive.issuer()) || squad.aliveMembers() <= 0) continue;
                if (roadColumn.contains(squad.squadId())) {
                    if (!tactics.roadColumnCommitted()) continue;
                    var target = sites.get(tactics.roadTarget());
                    if (Math.hypot(squad.centroidX() - target.captureCellX(), squad.centroidY() - target.captureCellY()) <= ROAD_ARRIVAL)
                        roadOnTarget = true;
                } else if (Math.hypot(squad.centroidX() - 59, squad.centroidY() - 23) <= 5) reserveOnApproach = true;
            }
        int target = faction == Faction.MARINE ? tactics.assaultTarget(states)
                : faction == Faction.ALLY ? tactics.militiaTarget()
                : tactics.reserveCommitted() ? tactics.firstSecured() : 3;
        var place = sites.get(target);
        String reason = faction == Faction.MARINE ? "JUNCTION_SECURE_SITE"
                : faction == Faction.ALLY ? (target == 0 ? "JUNCTION_COVER_LANDING" : "JUNCTION_HOLD_FIRST_GAIN")
                : tactics.reserveCommitted() ? (reserveOnApproach ? "JUNCTION_RETAKE_FIRST_LOSS" : "JUNCTION_RESERVE_SERVICE_ROAD") : "JUNCTION_RESERVE_WAIT";
        List<CommandProposal> proposals = new ArrayList<>();
        List<Integer> commanded = new ArrayList<>();
        for (var squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            CommandDirective old = squad.directive();
            boolean ours = old != null && (old.authority() == CommandAuthority.MISSION_COMMAND && strategyId().equals(old.issuer())
                    || old.authority() == CommandAuthority.PLAYER_INTERVENTION && old.leaseUntilTick() >= 0 && old.leaseUntilTick() < frame.tick());
            if (!ours) {
                proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, "EXTERNAL_OWNERSHIP_PRESERVED"));
                continue;
            }
            commanded.add(squad.squadId());
            if (faction == Faction.DEFENDER) {
                garrisons.own(squad);
                // A column waiting at its post holds the windows near it; a committed one marches as authored.
                if (roadColumn.contains(squad.squadId())) {
                    if (!tactics.roadColumnCommitted()) garrisons.offerDuty(squad, roadX, roadY);
                } else if (!tactics.reserveCommitted()) garrisons.offerDuty(squad, reserveX, reserveY);
            }
            if (faction == Faction.DEFENDER && roadColumn.contains(squad.squadId())) {
                ObjectiveAssignment road = roadAssignment(squad.squadId(), sites);
                String roadReason = !tactics.roadColumnCommitted() ? "JUNCTION_ROAD_WAIT"
                        : roadOnTarget ? "JUNCTION_RETAKE_SECOND_LOSS" : "JUNCTION_ROAD_COUNTERATTACK";
                if (sameAssignment(old.assignment(), road)) road = old.assignment();
                proposals.add(CommandProposal.assign(road, CommandAuthority.MISSION_COMMAND, roadReason,
                        road == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED));
                continue;
            }
            ObjectiveAssignment assignment = faction == Faction.MARINE
                    ? ObjectiveAssignment.secureCompound(squad.squadId(), place.captureZoneId(), place.node())
                    : faction == Faction.DEFENDER && !tactics.reserveCommitted()
                    ? ObjectiveAssignment.defendArea(squad.squadId(), reserveX, reserveY, 3)
                    : faction == Faction.DEFENDER && !reserveOnApproach
                    ? ObjectiveAssignment.attackMove(squad.squadId(), 59, 23)
                    : ObjectiveAssignment.defendArea(squad.squadId(), place.captureCellX(), place.captureCellY(), 3);
            if (sameAssignment(old.assignment(), assignment)) assignment = old.assignment();
            proposals.add(CommandProposal.assign(assignment, CommandAuthority.MISSION_COMMAND, reason,
                    assignment == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED));
        }
        if (faction == Faction.DEFENDER) proposals = garrisons.apply(frame, frame.facts().garrisonables(), proposals, strategyId());
        int influenceTick = frame.influence() == null ? -1 : frame.influence().updatedTick();
        return new CommandPlan<>(faction, strategyId(), reason, frame.tick(), influenceTick,
                commanded.size(), faction == Faction.DEFENDER && !tactics.reserveCommitted() ? commanded.size() : 0,
                List.of(JunctionCompound.SITE_NAMES.get(target)), proposals,
                new Picture(target, tactics.reserveCommitted(), reserveOnApproach, commanded));
    }
    /** The Rill road column waits at its post, marches on the second gain, then holds its capture cell. */
    private ObjectiveAssignment roadAssignment(int squadId, List<ConquestCommandFacts.Compound> sites) {
        if (!tactics.roadColumnCommitted()) return ObjectiveAssignment.defendArea(squadId, roadX, roadY, 3);
        var target = sites.get(tactics.roadTarget());
        return roadOnTarget ? ObjectiveAssignment.defendArea(squadId, target.captureCellX(), target.captureCellY(), 3)
                : ObjectiveAssignment.attackMove(squadId, target.captureCellX(), target.captureCellY());
    }
    private static boolean sameAssignment(ObjectiveAssignment a, ObjectiveAssignment b) {
        return a != null && a.kind() == b.kind() && a.targetZoneId() == b.targetZoneId()
                && a.targetCellX() == b.targetCellX() && a.targetCellY() == b.targetCellY()
                && a.targetRadiusCells() == b.targetRadiusCells()
                && CommandFrameCopies.sameNodeIdentity(a.targetNode(), b.targetNode());
    }
}
