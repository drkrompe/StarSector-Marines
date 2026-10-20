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
    /** The defender's finite pool on the shared counter-attack (`place-counterattack.md`); {@code null} for the other sides. */
    private final SiteCounterattack counter;
    public RelayNineCommand(Faction faction) {
        if (faction != Faction.MARINE && faction != Faction.ALLY && faction != Faction.DEFENDER)
            throw new IllegalArgumentException("Relay Nine requires a fighting faction");
        this.faction = faction;
        this.counter = faction == Faction.DEFENDER ? new SiteCounterattack(3, 1, 3) : null;
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
        Map<Integer, SiteCounterattack.Duty> retake = counter != null && PlaceCounterattack.isEnabled()
                ? counter.plan(frame.tick(), sites, owned(frame), frame.squads(), frame.influence(), frame.topology(), "RELAY")
                : null;
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
            SiteCounterattack.Duty duty = retake == null ? null : retake.get(squad.squadId());
            if (duty != null) {
                ObjectiveAssignment assignment = same(old.assignment(), duty.assignment()) ? old.assignment() : duty.assignment();
                proposals.add(CommandProposal.assign(assignment, CommandAuthority.MISSION_COMMAND, duty.reason(),
                        assignment == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED));
                duties.add(new Duty(squad.squadId(), site, duty.reason()));
                continue;
            }
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
                summaries(duties), proposals, new Picture(duties));
    }
    private List<String> summaries(List<Duty> duties) {
        List<String> out = new ArrayList<>(duties.stream().map(d -> RelayNineCompound.SITE_NAMES.get(d.site())).distinct().toList());
        if (counter != null && PlaceCounterattack.isEnabled()) out.addAll(counter.notes());
        return out;
    }
    /** The squads this commander moves: alive and under its own mission directive. */
    private List<CommandSquadState> owned(ConquestCommandFrame frame) {
        List<CommandSquadState> out = new ArrayList<>();
        for (var squad : frame.squads()) {
            var old = squad.directive();
            if (squad.aliveMembers() > 0 && old != null && old.authority() == CommandAuthority.MISSION_COMMAND
                    && strategyId().equals(old.issuer())) out.add(squad);
        }
        return out;
    }
    private static boolean same(ObjectiveAssignment a, ObjectiveAssignment b) {
        return a != null && a.kind() == b.kind() && a.targetZoneId() == b.targetZoneId()
                && a.targetCellX() == b.targetCellX() && a.targetCellY() == b.targetCellY()
                && a.targetRadiusCells() == b.targetRadiusCells()
                && CommandFrameCopies.sameNodeIdentity(a.targetNode(), b.targetNode());
    }
}
