package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.JunctionCompound;
import java.util.ArrayList;
import java.util.List;

/** Mission-specific duties over frozen public territory and own-force reports only. */
public final class JunctionCommand implements AutonomousMissionCommand<ConquestCommandFrame, JunctionCommand.Picture> {
    public record Picture(int targetSite, boolean reserveCommitted, List<Integer> commandedSquads) {
        public Picture { commandedSquads = List.copyOf(commandedSquads); }
    }
    private final Faction faction;
    private final int reserveX, reserveY;
    private final JunctionTactics tactics = new JunctionTactics();
    private volatile Picture picture;
    private boolean reserveOnApproach;
    public JunctionCommand(Faction faction, int reserveX, int reserveY) {
        if (faction != Faction.MARINE && faction != Faction.ALLY && faction != Faction.DEFENDER)
            throw new IllegalArgumentException("Junction command requires a fighting faction");
        this.faction = faction;
        this.reserveX = reserveX; this.reserveY = reserveY;
    }
    public static String issuer(Faction faction) { return "junction:" + faction; }
    @Override public Faction faction() { return faction; }
    @Override public String strategyId() { return issuer(faction); }
    public Picture picture() { return picture; }
    @Override public void publish(CommanderSnapshot<Picture> snapshot) { picture = snapshot.detail(); }

    @Override public CommandPlan<Picture> plan(ConquestCommandFrame frame) {
        var sites = frame.facts().compounds();
        var states = sites.stream().map(ConquestCommandFacts.Compound::state).toList();
        tactics.observe(states);
        if (faction == Faction.DEFENDER && tactics.reserveCommitted())
            for (var squad : frame.squads()) {
                var directive = squad.directive();
                if (directive != null && strategyId().equals(directive.issuer()) && squad.aliveMembers() > 0
                        && Math.hypot(squad.centroidX() - 59, squad.centroidY() - 23) <= 5) reserveOnApproach = true;
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
        int influenceTick = frame.influence() == null ? -1 : frame.influence().updatedTick();
        return new CommandPlan<>(faction, strategyId(), reason, frame.tick(), influenceTick,
                commanded.size(), faction == Faction.DEFENDER && !tactics.reserveCommitted() ? commanded.size() : 0,
                List.of(JunctionCompound.SITE_NAMES.get(target)), proposals,
                new Picture(target, tactics.reserveCommitted(), commanded));
    }
    private static boolean sameAssignment(ObjectiveAssignment a, ObjectiveAssignment b) {
        return a != null && a.kind() == b.kind() && a.targetZoneId() == b.targetZoneId()
                && a.targetCellX() == b.targetCellX() && a.targetCellY() == b.targetCellY()
                && a.targetRadiusCells() == b.targetRadiusCells()
                && CommandFrameCopies.sameNodeIdentity(a.targetNode(), b.targetNode());
    }
}
