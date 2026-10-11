package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.RelayNineCompound;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Separate assault, two stable militia holds, and a finite counterattack pool. */
public final class RelayNineCommand implements AutonomousMissionCommand<ConquestCommandFrame, RelayNineCommand.Picture> {
    public record Duty(int squadId, int site, String reason) {}
    public record Picture(List<Duty> duties) { public Picture { duties = List.copyOf(duties); } }
    private final Faction faction;
    private final RelayNineTactics tactics = new RelayNineTactics();
    private final Map<Integer, Integer> detachments = new LinkedHashMap<>();
    private volatile Picture picture;
    public RelayNineCommand(Faction faction) {
        if (faction != Faction.MARINE && faction != Faction.ALLY && faction != Faction.DEFENDER)
            throw new IllegalArgumentException("Relay Nine requires a fighting faction");
        this.faction = faction;
    }
    public static String issuer(Faction faction) { return "relay-nine:" + faction; }
    @Override public Faction faction() { return faction; }
    @Override public String strategyId() { return issuer(faction); }
    public Picture picture() { return picture; }
    @Override public void publish(CommanderSnapshot<Picture> snapshot) { picture = snapshot.detail(); }
    @Override public CommandPlan<Picture> plan(ConquestCommandFrame frame) {
        var sites = frame.facts().compounds();
        var states = sites.stream().map(ConquestCommandFacts.Compound::state).toList();
        tactics.observe(states);
        List<CommandProposal> proposals = new ArrayList<>();
        List<Duty> duties = new ArrayList<>();
        for (var squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            var old = squad.directive();
            if (old == null || old.authority() != CommandAuthority.MISSION_COMMAND || !strategyId().equals(old.issuer())) {
                proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, "EXTERNAL_OWNERSHIP_PRESERVED"));
                continue;
            }
            int site = faction == Faction.MARINE ? tactics.assaultTarget(states)
                    : faction == Faction.DEFENDER ? tactics.counterTarget(states)
                    : tactics.militiaTarget(detachments.computeIfAbsent(squad.squadId(), id -> detachments.size()));
            var place = sites.get(site);
            String reason = faction == Faction.MARINE ? "RELAY_SECURE_SITE"
                    : faction == Faction.DEFENDER ? "RELAY_RETAKE_GAIN"
                    : site == 0 ? "RELAY_COVER_LANDING" : "RELAY_HOLD_GAIN";
            ObjectiveAssignment assignment = faction == Faction.MARINE
                    ? ObjectiveAssignment.secureCompound(squad.squadId(), place.captureZoneId(), place.node())
                    : ObjectiveAssignment.defendArea(squad.squadId(), place.captureCellX(), place.captureCellY(), 3);
            if (same(old.assignment(), assignment)) assignment = old.assignment();
            proposals.add(CommandProposal.assign(assignment, CommandAuthority.MISSION_COMMAND, reason,
                    assignment == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED));
            duties.add(new Duty(squad.squadId(), site, reason));
        }
        return new CommandPlan<>(faction, strategyId(), faction == Faction.ALLY ? "HOLD_GAINS"
                : faction == Faction.MARINE ? "CAPTURE_AND_RESTORE" : "FINITE_COUNTERATTACK", frame.tick(),
                frame.influence() == null ? -1 : frame.influence().updatedTick(), duties.size(), 0,
                duties.stream().map(d -> RelayNineCompound.SITE_NAMES.get(d.site())).distinct().toList(), proposals, new Picture(duties));
    }
    private static boolean same(ObjectiveAssignment a, ObjectiveAssignment b) {
        return a != null && a.kind() == b.kind() && a.targetZoneId() == b.targetZoneId()
                && a.targetCellX() == b.targetCellX() && a.targetCellY() == b.targetCellY()
                && a.targetRadiusCells() == b.targetRadiusCells()
                && CommandFrameCopies.sameNodeIdentity(a.targetNode(), b.targetNode());
    }
}
