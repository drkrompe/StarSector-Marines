package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.setup.LimitedWarrantSequence;
import com.dillon.starsectormarines.battle.story.LimitedWarrantProgress.Phase;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LowQuay;
import java.util.ArrayList;
import java.util.List;

/** Recovery duties consume disclosed work stages; local squads retain combat and player orders. */
public final class LimitedWarrantCommand implements AutonomousMissionCommand<LimitedWarrantCommand.Frame, String> {
    public static final class Frame extends CommandFrame {
        final Phase phase;
        final int x, y, technician;
        final boolean companyCover;
        /** The company's nearest known enemy near the lane crossing, or null. */
        final CommanderContact crossingThreat;
        public Frame(CommandFrame base, Phase phase, int x, int y, int technician, boolean companyCover, CommanderContact crossingThreat) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            this.phase = phase; this.x = x; this.y = y; this.technician = technician;
            this.companyCover = companyCover; this.crossingThreat = crossingThreat;
        }
    }
    public static CommandFrameDisclosure<Frame> disclosure(LimitedWarrantSequence sequence) {
        return (view, faction, topology, assignments) -> {
            var base = CommandFrame.freeze(view, faction, topology, assignments);
            // The rearguard knows the lane crossing every extraction must pass, never the hidden
            // company or package position.
            int x = faction == Faction.DEFENDER ? LowQuay.CROSS_X : sequence.targetX();
            int y = faction == Faction.DEFENDER ? LowQuay.CROSS_Y : sequence.targetY();
            boolean covered = false;
            CommanderContact crossing = null;
            if (faction != Faction.DEFENDER) {
                var company = faction == Faction.MARINE ? base : CommandFrame.freeze(view, Faction.MARINE, topology, assignments);
                // The company's own picture of the lane crossing every route in the compound passes.
                if (company.influence() != null) crossing = crossingThreat(company.influence().contacts());
                if (faction == Faction.ALLY) {
                    boolean alarm = company.influence() != null
                            && WorkingPassageCommand.constructionThreat(company.influence().contacts(), x, y) != null;
                    covered = !alarm && company.squads().stream().anyMatch(s -> s.aliveMembers() > 0
                            && Math.hypot(s.centroidX() - x, s.centroidY() - y) < 20);
                }
            }
            return new Frame(base, sequence.progress().phase(), x, y, sequence.technicianSquadId(), covered, crossing);
        };
    }
    /** Radius around the crossing covering the lane, both gallery vestibules and their first stretch. */
    static final int CROSSING_THREAT_CELLS = 16;
    static CommanderContact crossingThreat(List<CommanderContact> contacts) {
        CommanderContact nearest = null; double best = CROSSING_THREAT_CELLS * CROSSING_THREAT_CELLS;
        for (var contact : contacts) {
            double dx = contact.cellX() - LowQuay.CROSS_X, dy = contact.cellY() - LowQuay.CROSS_Y, distance = dx*dx + dy*dy;
            if (distance <= best && (nearest == null || distance < best || contact.unitId() < nearest.unitId())) { nearest = contact; best = distance; }
        }
        return nearest;
    }
    private final Faction faction;
    public LimitedWarrantCommand(Faction faction) { this.faction = faction; }
    public static String issuer(Faction faction) { return "limited-warrant:" + faction; }
    public Faction faction() { return faction; }
    public String strategyId() { return issuer(faction); }
    public void publish(CommanderSnapshot<String> snapshot) { }
    public CommandPlan<String> plan(Frame frame) {
        var proposals = new ArrayList<CommandProposal>(); int count = 0;
        // With two or more squads ashore, the highest-numbered one holds the lane crossing while the
        // supplier's technician walks in; the rest cover the calibration shop.
        int routeSquad = -1, alive = 0;
        for (var squad : frame.squads()) if (squad.aliveMembers() > 0) { alive++; routeSquad = Math.max(routeSquad, squad.squadId()); }
        if (alive < 2) routeSquad = -1;
        for (var squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            var old = squad.directive();
            boolean owned = old != null && (old.authority() == CommandAuthority.MISSION_COMMAND && strategyId().equals(old.issuer())
                    || old.authority() == CommandAuthority.PLAYER_INTERVENTION && old.leaseUntilTick() >= 0 && old.leaseUntilTick() < frame.tick());
            if (!owned) { proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, "EXTERNAL_OWNERSHIP_PRESERVED")); continue; }
            int x = frame.x, y = frame.y, radius = 3;
            boolean advance = faction == Faction.DEFENDER;
            if (faction == Faction.MARINE && frame.phase == Phase.CALIBRATION && squad.squadId() == routeSquad) {
                // Hold the crossing; clear a known enemy near it rather than waiting it out.
                if (frame.crossingThreat != null) { x = frame.crossingThreat.cellX(); y = frame.crossingThreat.cellY(); advance = true; }
                else { x = LowQuay.CROSS_X; y = LowQuay.CROSS_Y; radius = 4; }
            } else if (faction == Faction.MARINE) {
                if (frame.phase != Phase.EGRESS) x += squad.squadId() % 2 == 0 ? -2 : 1;
                if (frame.influence() != null) {
                    var threat = WorkingPassageCommand.constructionThreat(frame.influence().contacts(), frame.x, frame.y);
                    if (threat != null) {
                        var point = WorkingPassageCommand.screenPoint(frame.x, frame.y, threat, squad.squadId() % 2 == 0 ? 1 : -1, old.assignment());
                        x = point[0]; y = point[1];
                    }
                }
            } else if (faction == Faction.ALLY) {
                if (squad.squadId() != frame.technician) { x = 37; y = 59; radius = 5; }
                else if (frame.phase == Phase.EGRESS) { x = LowQuay.LANDING_X; y = LowQuay.LANDING_Y; advance = true; }
                else if (frame.phase == Phase.RECORDS || !frame.companyCover || frame.crossingThreat != null) {
                    x = Math.round(squad.centroidX()); y = Math.round(squad.centroidY());
                } else { advance = true; radius = 2; }
            }
            int best = Integer.MAX_VALUE, bx = Math.round(squad.centroidX()), by = Math.round(squad.centroidY());
            for (int cy = y - 5; cy <= y + 5; cy++) for (int cx = x - 5; cx <= x + 5; cx++) {
                int d = (cx-x)*(cx-x)+(cy-y)*(cy-y);
                if (d < best && frame.topology().isWalkable(cx, cy) && frame.topology().reachable(squad.anchorCellX(), squad.anchorCellY(), cx, cy)) {
                    best = d; bx = cx; by = cy;
                }
            }
            var next = advance ? ObjectiveAssignment.attackMove(squad.squadId(), bx, by)
                    : ObjectiveAssignment.defendArea(squad.squadId(), bx, by, radius);
            if (old.assignment() != null && old.assignment().kind() == next.kind()
                    && old.assignment().targetCellX() == bx && old.assignment().targetCellY() == by
                    && old.assignment().targetRadiusCells() == next.targetRadiusCells()) next = old.assignment();
            proposals.add(CommandProposal.assign(next, CommandAuthority.MISSION_COMMAND, "LIMITED_WARRANT_" + frame.phase,
                    next == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED)); count++;
        }
        return new CommandPlan<>(faction, strategyId(), "LIMITED_WARRANT_" + frame.phase, frame.tick(),
                frame.influence() == null ? -1 : frame.influence().updatedTick(), count, 0, List.of(frame.phase.name()), proposals, frame.phase.name());
    }
}
