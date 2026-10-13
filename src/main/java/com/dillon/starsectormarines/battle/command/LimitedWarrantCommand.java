package com.dillon.starsectormarines.battle.command;

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
        public Frame(CommandFrame base, Phase phase, int x, int y, int technician, boolean companyCover) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            this.phase = phase; this.x = x; this.y = y; this.technician = technician; this.companyCover = companyCover;
        }
    }
    public static CommandFrameDisclosure<Frame> disclosure(LimitedWarrantSequence sequence) {
        return (view, faction, topology, assignments) -> {
            var base = CommandFrame.freeze(view, faction, topology, assignments);
            // The rearguard knows the loading exit, never the hidden company or package position.
            int x = faction == Faction.DEFENDER ? 48 : sequence.targetX();
            int y = faction == Faction.DEFENDER ? 59 : sequence.targetY();
            boolean covered = false;
            if (faction == Faction.ALLY) {
                var company = CommandFrame.freeze(view, Faction.MARINE, topology, assignments);
                boolean alarm = company.influence() != null
                        && WorkingPassageCommand.constructionThreat(company.influence().contacts(), x, y) != null;
                covered = !alarm && company.squads().stream().anyMatch(s -> s.aliveMembers() > 0
                        && Math.hypot(s.centroidX() - x, s.centroidY() - y) < 20);
            }
            return new Frame(base, sequence.progress().phase(), x, y, sequence.technicianSquadId(), covered);
        };
    }
    private final Faction faction;
    public LimitedWarrantCommand(Faction faction) { this.faction = faction; }
    public static String issuer(Faction faction) { return "limited-warrant:" + faction; }
    public Faction faction() { return faction; }
    public String strategyId() { return issuer(faction); }
    public void publish(CommanderSnapshot<String> snapshot) { }
    public CommandPlan<String> plan(Frame frame) {
        var proposals = new ArrayList<CommandProposal>(); int count = 0;
        for (var squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            var old = squad.directive();
            boolean owned = old != null && (old.authority() == CommandAuthority.MISSION_COMMAND && strategyId().equals(old.issuer())
                    || old.authority() == CommandAuthority.PLAYER_INTERVENTION && old.leaseUntilTick() >= 0 && old.leaseUntilTick() < frame.tick());
            if (!owned) { proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, "EXTERNAL_OWNERSHIP_PRESERVED")); continue; }
            int x = frame.x, y = frame.y, radius = 3;
            boolean advance = faction == Faction.DEFENDER;
            if (faction == Faction.MARINE) {
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
                else if (frame.phase == Phase.RECORDS || !frame.companyCover) {
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
